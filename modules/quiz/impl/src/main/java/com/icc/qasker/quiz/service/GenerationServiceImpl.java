package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.FeRequestToAIRequestMapper;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
            return emitter;
        }

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));

        ProblemSet finalSaveProblemSet = saveProblemSet;
        Thread.ofVirtual().start(() -> {
            try {
                aiServerAdapter.streamRequest(
                    FeRequestToAIRequestMapper.toAIRequest(request),
                    (quiz) -> {
                        if (cancelled.get()) {
                            return;
                        }
                        doMainLogic(request, quiz, emitter, finalSaveProblemSet);
                    }
                );

                Long problemSetId = finalSaveProblemSet.getId();
                long generatedCount = problemRepository.countByIdProblemSetId(
                    finalSaveProblemSet.getId());
                if (generatedCount == 0) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
                }
                if (generatedCount == request.quizCount()) {
                    finalizeSuccess(request, problemSetId, emitter);
                } else {
                    finalizePartialSuccess(
                        request,
                        generatedCount,
                        problemSetId,
                        emitter
                    );
                }
            } catch (Exception e) {
                finalizeError(request, finalSaveProblemSet.getId(), emitter, e);
            }
        });
        return emitter;
    }

    private void doMainLogic(GenerationRequest request, ProblemSetGeneratedEvent quiz,
        SseEmitter emitter,
        ProblemSet saveProblemSet) {
        try {
            String encodedId = hashUtil.encode(saveProblemSet.getId());
            List<Problem> problems = new ArrayList<>();
            List<QuizForFe> quizForFeList = new ArrayList<>();
            for (QuizGeneratedFromAI quizGeneratedFromAI : quiz.getQuiz()) {
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

    private void finalizeSuccess(
        GenerationRequest request,
        Long problemSetId,
        SseEmitter emitter
    ) {
        emitter.complete();
        slackNotifier.asyncNotifyText("""
            ✅ [퀴즈 생성 완료 알림]
            ProblemSetId: %s
            퀴즈 타입: %s
            문제 수: %d
            """.formatted(
            hashUtil.encode(problemSetId),
            request.quizType(),
            request.quizCount()
        ));
    }

    private void finalizePartialSuccess(
        GenerationRequest request,
        long generatedCount,
        Long problemSetId,
        SseEmitter emitter
    ) {
        emitter.complete();
        slackNotifier.asyncNotifyText("""
            ⚠️ [퀴즈 생성 부분 완료]
            ProblemSetId: %s
            생성된 문제 수: %d개 중 %d개
            """.formatted(
            hashUtil.encode(problemSetId),
            request.quizCount(),
            generatedCount
        ));
    }

    private void finalizeError(
        GenerationRequest request,
        Long problemSetId,
        SseEmitter emitter,
        Exception e
    ) {
        sendErrorAndComplete(emitter, e);
        // 보상 트랜잭션 수행
        // 영속성 컨텍스트 추가 -> 삭제 비용
        // problemSetRepository.delete(problemSet);
        problemSetRepository.deleteById(problemSetId);
        slackNotifier.asyncNotifyText("""
            ❌ [퀴즈 생성 실패]
            사유: %s
            """.formatted(
            e.getMessage()
        ));
    }

    private void sendErrorAndComplete(SseEmitter emitter, Exception e) {
        try {
            emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            emitter.complete();
        } catch (IOException ignored) {
        }
    }
}
