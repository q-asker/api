package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.COMPLETED;
import static com.icc.qasker.quizset.GenerationStatus.FAILED;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizmake.GenerationCommandService;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.adapter.AIServerAdapter;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizset.QualityLogService;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GenerationCommandServiceImpl implements GenerationCommandService {

  private final AIServerAdapter aiServerAdapter;
  private final SseNotificationService notificationService;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final HashUtil hashUtil;
  private final GenerationResultRecorder resultRecorder;
  private final QualityLogService qualityLogService;

  public GenerationCommandServiceImpl(
      AIServerAdapter aiServerAdapter,
      SseNotificationService notificationService,
      QuizCommandService quizCommandService,
      QuizQueryService quizQueryService,
      HashUtil hashUtil,
      GenerationResultRecorder resultRecorder,
      QualityLogService qualityLogService) {
    this.aiServerAdapter = aiServerAdapter;
    this.notificationService = notificationService;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.hashUtil = hashUtil;
    this.resultRecorder = resultRecorder;
    this.qualityLogService = qualityLogService;
  }

  @Override
  public void triggerGeneration(String userId, GenerationRequest request) {
    Long problemSetId;
    try {
      problemSetId =
          quizCommandService.initProblemSet(
              userId,
              request.sessionId(),
              request.title(),
              request.quizCount(),
              request.quizType(),
              request.uploadedUrl(),
              request.customInstruction());
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
    }

    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    Thread.ofVirtual()
        .start(
            () -> {
              if (contextMap != null) {
                MDC.setContextMap(contextMap);
              }
              try {
                processGenerationAsync(request.sessionId(), problemSetId, request);
              } finally {
                MDC.clear();
              }
            });
  }

  private void processGenerationAsync(
      String sessionId, Long problemSetId, GenerationRequest request) {

    long startNanos = System.nanoTime();
    GenerationBatchConsumer batchConsumer =
        new GenerationBatchConsumer(
            sessionId,
            problemSetId,
            request,
            quizCommandService,
            quizQueryService,
            notificationService,
            hashUtil,
            qualityLogService);

    GenerationRequestToAI requestToAI =
        GenerationRequestToAI.builder()
            .fileUrl(request.uploadedUrl())
            .strategyValue(request.quizType().toAiStrategyName())
            .language(request.language().name())
            .quizCount(request.quizCount())
            .referencePages(request.pageNumbers())
            .customInstruction(request.customInstruction())
            .sink(batchConsumer)
            .build();

    int maxChunkCount;
    try {
      maxChunkCount = aiServerAdapter.streamRequest(requestToAI);
    } catch (Exception e) {
      log.error("[AI 생성 실패] 퀴즈 생성 중 오류 발생 sessionId={}", sessionId, e);
      finalizeError(
          sessionId,
          problemSetId,
          request.quizType(),
          ExceptionMessage.AI_GENERATION_FAILED.getMessage());
      return;
    }

    int generatedCount = batchConsumer.generatedCount();
    int quizCount = request.quizCount();
    long ttfqMs =
        batchConsumer.firstConsumerNanos() > 0
            ? (batchConsumer.firstConsumerNanos() - startNanos) / 1_000_000
            : -1;
    long ttlqMs =
        batchConsumer.lastConsumerNanos() > 0
            ? (batchConsumer.lastConsumerNanos() - startNanos) / 1_000_000
            : -1;

    // 요청/생성/실패 문제 수 메트릭 기록 (finalize 결과와 무관하게 항상 실행)
    resultRecorder.recordQuizCounts(request.quizType(), quizCount, generatedCount, maxChunkCount);

    if (generatedCount == 0) {
      finalizeError(
          sessionId,
          problemSetId,
          request.quizType(),
          ExceptionMessage.AI_GENERATION_FAILED.getMessage());
    } else if (generatedCount == quizCount) {
      finalizeSuccess(sessionId, problemSetId, request.quizType(), generatedCount, ttfqMs, ttlqMs);
    } else {
      finalizePartialSuccess(
          sessionId, problemSetId, request.quizType(), generatedCount, quizCount, ttfqMs, ttlqMs);
    }
  }

  private void finalizeSuccess(
      String sessionId,
      Long problemSetId,
      QuizType quizType,
      long generatedCount,
      long ttfqMs,
      long ttlqMs) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    resultRecorder.recordSuccess(problemSetId, quizType, generatedCount, ttfqMs, ttlqMs);
  }

  private void finalizePartialSuccess(
      String sessionId,
      Long problemSetId,
      QuizType quizType,
      long generatedCount,
      long quizCount,
      long ttfqMs,
      long ttlqMs) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    resultRecorder.recordPartialSuccess(
        problemSetId, quizType, generatedCount, quizCount, ttfqMs, ttlqMs);
  }

  private void finalizeError(
      String sessionId, Long problemSetId, QuizType quizType, String errorMessage) {
    quizCommandService.updateStatus(problemSetId, FAILED);
    notificationService.sendFinishWithError(sessionId, errorMessage);
    resultRecorder.recordError(problemSetId, quizType, errorMessage);
  }
}
