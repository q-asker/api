package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.COMPLETED;

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
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * mock 프로파일 생성 커맨드(@Primary). 실제 구현과 달리 가상 스레드로 넘기지 않고 요청 스레드에서 동기로 픽스처 파이프라인(MockAIServerAdapter)을
 * 끝까지 태운다 — 부하 계측(request.thread.* 힙·CPU, X-Query-Count)이 생성 작업을 온전히 요청에 귀속하고, E2E는 응답 시점에 저장
 * 완료(COMPLETED)가 보장된다. GenerationBatchConsumer(패키지 전용)를 쓰기 위해 service.mock이 아니라 이 패키지에 둔다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockGenerationCommandService implements GenerationCommandService {

  private final AIServerAdapter aiServerAdapter;
  private final SseNotificationService notificationService;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final HashUtil hashUtil;
  private final QualityLogService qualityLogService;

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
              request.customInstruction(),
              request.pageNumbers(),
              request.language().name());
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
    }

    GenerationBatchConsumer batchConsumer =
        new GenerationBatchConsumer(
            request.sessionId(),
            problemSetId,
            request,
            quizCommandService,
            quizQueryService,
            notificationService,
            hashUtil,
            qualityLogService);

    aiServerAdapter.streamRequest(
        GenerationRequestToAI.builder()
            .fileUrl(request.uploadedUrl())
            .strategyValue(request.quizType().toAiStrategyName())
            .language(request.language().name())
            .quizCount(request.quizCount())
            .referencePages(request.pageNumbers())
            .customInstruction(request.customInstruction())
            .sink(batchConsumer)
            .build());

    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(request.sessionId());
  }
}
