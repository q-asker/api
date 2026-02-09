package com.icc.qasker.quiz.service.generation;

import static com.icc.qasker.quiz.GenerationStatus.COMPLETED;
import static com.icc.qasker.quiz.GenerationStatus.FAILED;
import static com.icc.qasker.quiz.GenerationStatus.GENERATING;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.QuizQueryService;
import com.icc.qasker.quiz.SseNotificationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.airequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.mapper.FeRequestToAIRequestMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
        // DB 통신 실패할 수 있으므로 먼저
        Optional<GenerationStatus> statusOptional = quizQueryService.getGenerationStatusBySessionId(
            sessionId);

        SseEmitter emitter = notificationService.createSseEmitter(sessionId);

        statusOptional.ifPresent(status -> {
            switch (status) {
                case FAILED -> notificationService.sendFinishWithError(sessionId,
                    ExceptionMessage.AI_GENERATION_FAILED.getMessage());

                case GENERATING, COMPLETED -> {
                    int lastEventNumber = NumberUtils.toInt(lastEventId, 0);
                    ProblemSetResponse ps = quizQueryService.getMissedProblems(
                        sessionId,
                        lastEventNumber);

                    notificationService.sendCreatedMessageWithId(
                        sessionId,
                        String.valueOf(lastEventNumber + ps.getQuiz().size()),
                        ps);

                    // COMPLETE 상태일 경우 완료 메시지 전송
                    if (status == COMPLETED) {
                        notificationService.sendComplete(sessionId);
                    }
                }
            }
        });

        return emitter;
    }

    public void triggerGeneration(
        String userId,
        GenerationRequest request) {
        // TOC
        Optional<GenerationStatus> status = quizQueryService.getGenerationStatusBySessionId(
            request.sessionId());
        if (status.isPresent()) {
            log.info("중복 요청 발생: sessionId: {}", request.sessionId());
            throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
        }

        // TOU
        Long problemSetId;
        try {
            problemSetId = quizCommandService.initProblemSet(
                userId,
                request.sessionId(),
                request.quizCount(),
                request.quizType());
        } catch (DataIntegrityViolationException e) {
            log.info("제약 조건 위반: sessionId={}", request.sessionId(), e);
            throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
        }

        Thread.ofVirtual()
            .uncaughtExceptionHandler((t, e) -> {
                log.error("가상 스레드 미처리 예외 발생: sessionId={}", request.sessionId(), e);
            })
            .start(() -> processAsyncGeneration(
                request.sessionId(),
                problemSetId,
                feRequestToAIRequestMapper.toAIRequest(request)));
    }

    private void processAsyncGeneration(
        String sessionId,
        Long problemSetId,
        GenerationRequestToAI request) {

        AtomicInteger atomicGeneratedCount = new AtomicInteger(0);
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
                        problemSetId);

                    if (quizForFeList.isEmpty()) {
                        log.warn("saveBatch 결과 빈 리스트 수신, 건너뜀: sessionId={}", sessionId);
                        return;
                    }

                    atomicGeneratedCount.addAndGet(quizForFeList.size());
                    notificationService.sendCreatedMessageWithId(
                        sessionId,
                        String.valueOf(quizForFeList.getLast().getNumber()),
                        new ProblemSetResponse(
                            sessionId,
                            hashUtil.encode(problemSetId),
                            GENERATING,
                            request.quizType(),
                            request.quizCount(),
                            quizForFeList));
                });
        } catch (Exception e) {
            finalizeError(sessionId, problemSetId,
                ExceptionMessage.AI_GENERATION_FAILED.getMessage());
            return;
        }

        int generatedCount = atomicGeneratedCount.get();
        if (generatedCount == 0) {
            finalizeError(sessionId, problemSetId,
                ExceptionMessage.AI_GENERATION_FAILED.getMessage());
        } else if (generatedCount == request.quizCount()) {
            finalizeSuccess(
                sessionId,
                problemSetId,
                request.quizType(),
                generatedCount);
        } else {
            finalizePartialSuccess(
                sessionId,
                problemSetId,
                request.quizType(),
                generatedCount,
                request.quizCount());
        }
    }

    private void finalizeSuccess(
        String sessionId,
        Long problemSetId,
        QuizType quizType,
        long generatedCount) {
        quizCommandService.updateStatus(problemSetId, COMPLETED);
        notificationService.sendComplete(sessionId);
        slackNotifier.asyncNotifyText("""
            ✅ [퀴즈 생성 완료 알림]
            ProblemSetId: %s
            퀴즈 타입: %s
            문제 수: %d
            """.formatted(
            hashUtil.encode(problemSetId),
            quizType,
            generatedCount));
    }

    private void finalizePartialSuccess(
        String sessionId,
        Long problemSetId,
        QuizType quizType,
        long generatedCount,
        long quizCount) {
        quizCommandService.updateStatus(problemSetId, COMPLETED);
        notificationService.sendComplete(sessionId);
        slackNotifier.asyncNotifyText("""
            ⚠️ [퀴즈 생성 부분 완료]
            ProblemSetId: %s
            퀴즈 타입: %s
            생성된 문제 수: %d개 / 총 문제 수: %d개
            """.formatted(
            hashUtil.encode(problemSetId),
            quizType,
            generatedCount,
            quizCount));
    }

    private void finalizeError(
        String sessionId,
        Long problemSetId,
        String errorMessage) {
        quizCommandService.updateStatus(problemSetId, FAILED);
        notificationService.sendFinishWithError(sessionId, errorMessage);
        slackNotifier.asyncNotifyText("""
            ❌ [퀴즈 생성 실패]
            ProblemSetId: %s
            원인: %s
            """.formatted(
            hashUtil.encode(problemSetId),
            errorMessage));
    }
}
