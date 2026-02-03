package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.mapper.FeRequestToAIRequestMapper;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    // 핵심
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final AIServerAdapter aiServerAdapter;
    private final QuizCommandService quizCommandService;
    // 유틸
    private final HashUtil hashUtil;
    private final SlackNotifier slackNotifier;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    // 매퍼
    private final FeRequestToAIRequestMapper feRequestToAIRequestMapper;
    private final ProblemSetResponseMapper problemSetResponseMapper;

    public SseEmitter subscribe(String sessionID, String lastEventID) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
        if (circuitBreaker.getState() == State.OPEN) {
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }

        SseEmitter emitter = new SseEmitter(100 * 1000L);
        emitterMap.put(sessionID, emitter);

        emitter.onCompletion(() -> emitterMap.remove(sessionID));
        emitter.onTimeout(() -> emitterMap.remove(sessionID));
        emitter.onError((e) -> emitterMap.remove(sessionID));
        return emitter;
    }

    public void triggerGeneration(
        String useId,
        GenerationRequest request
    ) {
        Long problemSetId = quizCommandService.initProblemSet(useId);
        String encodedId = hashUtil.encode(problemSetId);
        Thread.ofVirtual().start(() -> processAsyncGeneration(
            request.sessionId(),
            problemSetId,
            encodedId,
            feRequestToAIRequestMapper.toAIRequest(request)
        ));
    }

    private void processAsyncGeneration(UUID sessionId, Long problemSetId, String encodedId,
        GenerationRequestToAI request) {
        try {
            aiServerAdapter.streamRequest(
                request,
                (ProblemSetGeneratedEvent problemSet) -> {
                    List<Problem> problems = quizCommandService.saveAll(problemSet.getQuiz(),
                        problemSetId);

                    List<QuizForFe> quizForFeList = new ArrayList<>();
                    for (Problem problem : problems) {
                        quizForFeList.add(problemSetResponseMapper.fromEntity(problem));
                    }

                    notifyClient(sessionId, new ProblemSetResponse(
                        encodedId,
                        quizCommandService.getGenerationStatus(problemSetId).toString(),
                        request.quizCount(),
                        quizForFeList
                    ));
                }
            );

            long generatedCount = quizCommandService.getCount(problemSetId);
            if (generatedCount == 0) {
                throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
            } else if (generatedCount == request.quizCount()) {
                finalizeSuccess(problemSetId, encodedId, request.quizType(), generatedCount);
            } else {
                finalizePartialSuccess(problemSetId, request.quizCount(), generatedCount);
            }
        } catch (Exception e) {
            // 보상 트랜잭션 수행
            // 영속성 컨텍스트 추가 -> 삭제 비용
            // problemSetRepository.delete(problemSet);
            quizCommandService.delete(problemSetId);
            finalizeError(problemSetId, e.getMessage());
        }
    }

    private void notifyClient(UUID sessionId, ProblemSetResponse data) {
        SseEmitter emitter = emitterMap.get(sessionId);
        sendClientByEmitter(emitter, "quiz", data);
    }

    private void sendClientByEmitter(SseEmitter emitter, String eventName, Object data) {
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitter.complete();
                log.error("클라이언트에게 전송 중 에러 발생: {}", e.getMessage());
            }
        }
    }

    private void finalizeSuccess(
        Long problemSetId,
        String encodedId,
        QuizType quizType,
        long generatedCount
    ) {
        SseEmitter emitter = emitterMap.get(problemSetId);
        emitter.complete();
        slackNotifier.asyncNotifyText("""
            ✅ [퀴즈 생성 완료 알림]
            ProblemSetId: %s
            퀴즈 타입: %s
            문제 수: %d
            """.formatted(
            encodedId,
            quizType,
            generatedCount
        ));
    }

    private void finalizeError(
        Long problemSetId,
        String errorMessage
    ) {
        SseEmitter emitter = emitterMap.get(problemSetId);
        sendClientByEmitter(emitter, "error", errorMessage);
        slackNotifier.asyncNotifyText("""
            ❌ [퀴즈 생성 실패]
            사유: %s
            """.formatted(
            errorMessage
        ));
    }

    private void finalizePartialSuccess(
        Long problemSetId,
        long quizCount,
        long generatedCount
    ) {
        SseEmitter emitter = emitterMap.get(problemSetId);
        emitter.complete();
        slackNotifier.asyncNotifyText("""
            ⚠️ [퀴즈 생성 부분 완료]
            ProblemSetId: %s
            생성된 문제 수: %d개 중 %d개
            """.formatted(
            hashUtil.encode(problemSetId),
            quizCount,
            generatedCount
        ));
    }
}
