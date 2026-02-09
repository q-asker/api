package com.icc.qasker.quiz.service.generation;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.QuizQueryService;
import com.icc.qasker.quiz.SseNotificationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.mapper.FeRequestToAIRequestMapper;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    // 핵심
    private final AIServerAdapter aiServerAdapter;
    private final SseNotificationService notificationService;
    private final QuizCommandService quizCommandService;
    private final QuizQueryService quizQueryService;
    // 유틸
    private final HashUtil hashUtil;
    private final SlackNotifier slackNotifier;
    // 매퍼
    private final FeRequestToAIRequestMapper feRequestToAIRequestMapper;

    public SseEmitter subscribe(String sessionId, String lastEventId) {
        Optional<GenerationStatus> status = quizQueryService.getGenerationStatusBySessionId(
            sessionId);

        SseEmitter emitter = notificationService.createSseEmitter(sessionId);

        if (status.isEmpty()) {
            return emitter;
        }

        int lastEventNumber = NumberUtils.toInt(lastEventId, 0);
        ProblemSetResponse ps = quizQueryService.getMissedProblems(
            sessionId,
            lastEventNumber
        );

        notificationService.sendCreatedMessageWithId(
            sessionId,
            String.valueOf(lastEventNumber + ps.getQuiz().size()),
            ps
        );

        if (status.get() == GenerationStatus.COMPLETED) {
            notificationService.complete(sessionId);
        }
        return emitter;
    }

    public void triggerGeneration(
        String userId,
        GenerationRequest request
    ) {
        // TOC
        Optional<GenerationStatus> status = quizQueryService.getGenerationStatusBySessionId(
            request.sessionId());
        if (status.isPresent()) {
            log.info("중복 요청 발생: sessionId: {}", request.sessionId());
            return;
        }

        // TOU
        Long problemSetId;
        try {
            problemSetId = quizCommandService.initProblemSet(
                userId,
                request.sessionId(),
                request.quizCount(),
                request.quizType()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("제약 조건 위반: sessionId={}", request.sessionId(), e);
            return;
        }
        Thread.ofVirtual()
            .uncaughtExceptionHandler((t, e)
                -> log.error("가상 스레드 미처리 예외 발생: sessionId={}", request.sessionId(), e))
            .start(() -> processAsyncGeneration(
                request.sessionId(),
                problemSetId,
                feRequestToAIRequestMapper.toAIRequest(request)
            ));
    }

    private void processAsyncGeneration(
        String sessionId,
        Long problemSetId,
        GenerationRequestToAI request
    ) {

        String encodedId = hashUtil.encode(problemSetId);
        String errorMessage = "알수 없는 에러";

        try {
            aiServerAdapter.streamRequest(
                request,
                (ProblemSetGeneratedEvent problemSet) -> {
                    if (problemSet.getQuiz() == null || problemSet.getQuiz().isEmpty()) {
                        log.warn("빈 배치 수신, 건너뜀: sessionId={}", sessionId);
                        return;
                    }
                    List<QuizForFe> quizForFeList = quizCommandService.saveBatch(
                        problemSet.getQuiz(),
                        problemSetId
                    );

                    notificationService.sendCreatedMessageWithId(
                        sessionId,
                        String.valueOf(quizForFeList.getLast().getNumber()),
                        new ProblemSetResponse(
                            sessionId,
                            encodedId,
                            GenerationStatus.GENERATING,
                            request.quizType(),
                            request.quizCount(),
                            quizForFeList
                        )
                    );
                }
            );
        } catch (Exception e) {
            log.error("생성 중 오류 발생", e);
            errorMessage = e.getMessage();
        } finally {
            long generatedCount = quizQueryService.getCount(problemSetId);
            if (generatedCount == 0) {
                finalizeError(sessionId, problemSetId, errorMessage);
            } else if (generatedCount == request.quizCount()) {
                finalizeSuccess(
                    problemSetId,
                    encodedId,
                    sessionId,
                    request.quizType(),
                    generatedCount
                );
            } else {
                finalizePartialSuccess(
                    sessionId,
                    problemSetId,
                    request.quizCount(),
                    generatedCount
                );
            }
        }
    }

    private void finalizeSuccess(
        Long problemSetId,
        String encodedId,
        String sessionId,
        QuizType quizType,
        long generatedCount
    ) {
        quizCommandService.updateStatus(problemSetId, GenerationStatus.COMPLETED);
        notificationService.complete(sessionId);
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

    private void finalizePartialSuccess(
        String sessionId,
        Long problemSetId,
        long quizCount,
        long generatedCount
    ) {
        quizCommandService.updateStatus(problemSetId, GenerationStatus.COMPLETED);
        notificationService.complete(sessionId);
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

    private void finalizeError(
        String sessionId,
        Long problemSetId,
        String errorMessage
    ) {
        quizCommandService.updateStatus(problemSetId, GenerationStatus.FAILED);
        notificationService.finishWithError(sessionId, errorMessage);
        slackNotifier.asyncNotifyText("""
            ❌ [퀴즈 생성 실패]
            사유: %s
            """.formatted(
            errorMessage
        ));
    }
}
