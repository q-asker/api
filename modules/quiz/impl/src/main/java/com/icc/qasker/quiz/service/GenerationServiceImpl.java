package com.icc.qasker.quiz.service;

import static com.icc.qasker.quiz.GenerationStatus.COMPLETED;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizCommandService;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    // 핵심
    private final AIServerAdapter aiServerAdapter;
    private final QuizCommandService quizCommandService;
    private final SseNotificationService notificationService;
    // 유틸
    private final HashUtil hashUtil;
    private final SlackNotifier slackNotifier;
    // 매퍼
    private final FeRequestToAIRequestMapper feRequestToAIRequestMapper;

    public SseEmitter subscribe(String sessionId, String lastEventID) {
        GenerationStatus status = quizCommandService.getGenerationStatusBySessionId(sessionId);
        switch (status) {
            case COMPLETED -> {
                ProblemSetResponse ps = quizCommandService.getMissedProblems(
                    sessionId,
                    lastEventID
                );
                notificationService.sendToClient(
                    sessionId,
                    "created",
                    ps
                );
                notificationService.complete(sessionId);
            }
            case GENERATING -> {
                ProblemSetResponse ps = quizCommandService.getMissedProblems(
                    sessionId,
                    lastEventID
                );
                notificationService.sendToClient(
                    sessionId,
                    "created",
                    ps
                );
            }
            case NOT_EXIST -> {
                notificationService.sendToClient(sessionId, "error",
                    ExceptionMessage.PROBLEM_SET_NOT_FOUND.getMessage());
                notificationService.complete(sessionId);
            }
            case WAITING -> {

            }
        }
        return notificationService.createSseEmitter(sessionId);
    }

    public void triggerGeneration(
        String userId,
        GenerationRequest request
    ) {
        GenerationStatus status = quizCommandService.getGenerationStatusBySessionId(
            request.sessionId());
        if (status == GenerationStatus.GENERATING || status == COMPLETED) {
            return;
        }

        Long problemSetId = quizCommandService.initProblemSet(userId, request.quizCount(),
            request.quizType());
        String encodedId = hashUtil.encode(problemSetId);
        Thread.ofVirtual().start(() -> processAsyncGeneration(
            request.sessionId(),
            problemSetId,
            encodedId,
            feRequestToAIRequestMapper.toAIRequest(request)
        ));
    }

    private void processAsyncGeneration(
        String sessionId,
        Long problemSetId,
        String encodedId,
        GenerationRequestToAI request
    ) {
        try {
            aiServerAdapter.streamRequest(
                request,
                (ProblemSetGeneratedEvent problemSet) -> {
                    List<QuizForFe> quizForFeList = quizCommandService.saveBatch(
                        problemSet.getQuiz(), problemSetId);

                    notificationService.sendToClient(sessionId,
                        "created",
                        String.valueOf(quizForFeList.getLast().getNumber()),

                        new ProblemSetResponse(
                            sessionId,
                            encodedId,
                            quizCommandService.getGenerationStatus(problemSetId),
                            request.quizType(),
                            request.quizCount(),
                            quizForFeList
                        ));
                }
            );

            long generatedCount = quizCommandService.getCount(problemSetId);
            if (generatedCount == 0) {
                throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
            } else if (generatedCount == request.quizCount()) {
                finalizeSuccess(sessionId, encodedId, request.quizType(), generatedCount);
            } else {
                finalizePartialSuccess(
                    sessionId,
                    problemSetId,
                    request.quizCount(),
                    generatedCount
                );
            }
        } catch (Exception e) {
            // 보상 트랜잭션 수행
            // 영속성 컨텍스트 추가 -> 삭제 비용
            // problemSetRepository.delete(problemSet);
            quizCommandService.delete(problemSetId);
            finalizeError(sessionId, e.getMessage());
        }
    }

    private void finalizeSuccess(
        String sessionID,
        String encodedId,
        QuizType quizType,
        long generatedCount
    ) {
        notificationService.complete(sessionID);
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
        String sessionID,
        String errorMessage
    ) {
        notificationService.sendToClient(sessionID, "error", errorMessage);
        slackNotifier.asyncNotifyText("""
            ❌ [퀴즈 생성 실패]
            사유: %s
            """.formatted(
            errorMessage
        ));
    }

    private void finalizePartialSuccess(
        String sessionID,
        Long problemSetId,
        long quizCount,
        long generatedCount
    ) {
        notificationService.complete(sessionID);
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
