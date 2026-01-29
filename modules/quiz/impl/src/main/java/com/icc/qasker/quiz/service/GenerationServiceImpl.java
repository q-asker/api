package com.icc.qasker.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.aiResponse.GenerationResponseFromAI;
import com.icc.qasker.quiz.dto.aiResponse.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ProblemSetResponseMapper problemSetResponseMapper;
    private final SlackNotifier slackNotifier;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final ProblemRepository problemRepository;
    private final AIServerAdapter aiServerAdapter;
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter processGenerationRequest(
        GenerationRequest request, String userId) {
        SseEmitter emitter = new SseEmitter(110 * 1000L);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
        if (circuitBreaker.getState() == State.OPEN) {
            sendErrorAndComplete(emitter,
                new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR));
            return emitter;
        }

        ProblemSet saveProblemSet = null;
        try {
            ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
            saveProblemSet = problemSetRepository.save(problemSet);
        } catch (Exception e) {
            log.error("초기 저장 실패: {}", e.getMessage());
            sendErrorAndComplete(emitter, e);
        }

        ProblemSet finalSaveProblemSet = saveProblemSet;
        Thread.ofVirtual().start(() -> {
            try {
                aiServerAdapter.streamRequest(request,
                    (line) -> processLine(request, line, emitter, finalSaveProblemSet));
                finalizeSuccess(finalSaveProblemSet.getId(), request, emitter);
            } catch (Exception e) {
                finalizeError(emitter, e, finalSaveProblemSet);
            }
        });
        return emitter;
    }

    private void processLine(GenerationRequest request, String line, SseEmitter emitter,
        ProblemSet saveProblemSet) {
        try {
            String encodedId = hashUtil.encode(saveProblemSet.getId());
            List<Problem> problems = new ArrayList<>();
            List<QuizForFe> quizForFeList = new ArrayList<>();
            GenerationResponseFromAI dto = objectMapper.readValue(line,
                GenerationResponseFromAI.class);
            for (QuizGeneratedFromAI quizGeneratedFromAI : dto.getQuiz()) {
                Problem problem = Problem.of(quizGeneratedFromAI, saveProblemSet);
                problems.add(problem);
                quizForFeList.add(problemSetResponseMapper.fromEntity(problem));
            }
            problemRepository.saveAll(problems);
            emitter.send(
                new ProblemSetResponse(
                    encodedId,
                    request.quizCount(),
                    quizForFeList
                ));
        } catch (IOException ignored) {
        }
    }

    private void finalizeSuccess(Long problemSetId, GenerationRequest request,
        SseEmitter emitter) {
        String encodedId = hashUtil.encode(problemSetId);
        int count = problemRepository.countByProblemSetId(problemSetId);
        slackNotifier.asyncNotifyText("""
            ✅ [퀴즈 생성 완료 알림]
            ProblemSet ID: %s
            퀴즈 타입: %s
            문제 수: %d개 중 %d개 생성됨
            """.formatted(
            encodedId,
            request.quizType(),
            request.quizCount(),
            count
        ));
        emitter.complete();
    }

    private void finalizeError(SseEmitter emitter, Exception e, ProblemSet problemSet) {
        sendErrorAndComplete(emitter, e);
        // 영속성 컨텍스트 추가 -> 삭제 비용
//        problemSetRepository.delete(problemSet);
        problemSetRepository.deleteById(problemSet.getId());
    }

    private void sendErrorAndComplete(SseEmitter emitter, Exception e) {
        try {
            emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            emitter.complete();
        } catch (IOException ignored) {
        }
    }
}
