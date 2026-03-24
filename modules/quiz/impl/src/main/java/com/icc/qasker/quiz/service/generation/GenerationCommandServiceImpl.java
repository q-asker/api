package com.icc.qasker.quiz.service.generation;

import static com.icc.qasker.quiz.GenerationStatus.COMPLETED;
import static com.icc.qasker.quiz.GenerationStatus.FAILED;
import static com.icc.qasker.quiz.GenerationStatus.GENERATING;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationCommandService;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.QuizQueryService;
import com.icc.qasker.quiz.SseNotificationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.mapper.AIProblemSetMapper;
import com.icc.qasker.quiz.mapper.ExplanationMarkdownBuilder;
import com.icc.qasker.quiz.mapper.QuizViewToQuizForFeMapper;
import com.icc.qasker.quiz.view.QuizView;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
public class GenerationCommandServiceImpl implements GenerationCommandService {

  // 핵심
  private final AIServerAdapter aiServerAdapter;
  private final SseNotificationService notificationService;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  // 유틸
  private final HashUtil hashUtil;
  private final GenerationResultRecorder resultRecorder;

  // Prometheus 메트릭
  private final LongTaskTimer e2eDuration;

  public GenerationCommandServiceImpl(
      AIServerAdapter aiServerAdapter,
      SseNotificationService notificationService,
      QuizCommandService quizCommandService,
      QuizQueryService quizQueryService,
      HashUtil hashUtil,
      GenerationResultRecorder resultRecorder,
      MeterRegistry registry) {
    this.aiServerAdapter = aiServerAdapter;
    this.notificationService = notificationService;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.hashUtil = hashUtil;
    this.resultRecorder = resultRecorder;

    // E2E 파이프라인 LongTaskTimer — 진행 중인 장기 작업 실시간 감시용
    this.e2eDuration =
        LongTaskTimer.builder("quiz.generation.e2e.duration")
            .description("퀴즈 생성 E2E 파이프라인 소요 시간 (진행 중 포함)")
            .register(registry);
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
              request.quizType());
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
    }

    Thread.ofVirtual()
        .start(() -> processAsyncGeneration(request.sessionId(), problemSetId, request));
  }

  private void processAsyncGeneration(
      String sessionId, Long problemSetId, GenerationRequest request) {

    // 퀴즈 생성 E2E 소요 시간 측정 시작 (Prometheus 메트릭)
    LongTaskTimer.Sample e2eSample = e2eDuration.start();
    AtomicInteger atomicGeneratedCount = new AtomicInteger(0);

    try {
      aiServerAdapter.streamRequest(
          GenerationRequestToAI.builder()
              .fileUrl(request.uploadedUrl())
              .strategyValue(request.quizType().name())
              .quizCount(request.quizCount())
              .referencePages(request.pageNumbers())
              .questionsConsumer(
                  aiProblemSet -> {
                    ProblemSetGeneratedEvent problemSet = AIProblemSetMapper.toEvent(aiProblemSet);
                    if (CollectionUtils.isEmpty(problemSet.getQuiz())) {
                      log.warn("빈 배치 수신, 건너뜀: sessionId={}", sessionId);
                      return;
                    }

                    ProblemSetGeneratedEvent refinedProblemSet =
                        refineQuiz(problemSet, request.quizType());

                    List<Integer> savedNumbers =
                        quizCommandService.saveBatch(refinedProblemSet.getQuiz(), problemSetId);

                    List<QuizView> quizViews =
                        quizQueryService.getQuizViews(problemSetId, savedNumbers);
                    if (quizViews.isEmpty()) {
                      return;
                    }

                    List<QuizForFe> quizForFeList =
                        quizViews.stream().map(QuizViewToQuizForFeMapper::toQuizForFe).toList();

                    notificationService.sendCreatedMessageWithId(
                        sessionId,
                        String.valueOf(quizViews.getLast().getNumber()),
                        new ProblemSetResponse(
                            sessionId,
                            hashUtil.encode(problemSetId),
                            request.title(),
                            GENERATING,
                            QuizType.valueOf(request.quizType().name()),
                            request.quizCount(),
                            quizForFeList));

                    atomicGeneratedCount.addAndGet(quizViews.size());
                  })
              .errorConsumer(ex -> log.error("청크 에러: {}", ex.getMessage()))
              .build());
    } catch (Exception e) {
      log.error("생성 중 오류 발생: sessionId={}", sessionId, e);
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
      return;
    } finally {
      e2eSample.stop();
    }

    int generatedCount = atomicGeneratedCount.get();
    int quizCount = request.quizCount();

    // 요청/생성/실패 문제 수 메트릭 기록 (finalize 결과와 무관하게 항상 실행)
    resultRecorder.recordQuizCounts(quizCount, generatedCount);

    if (generatedCount == 0) {
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
    } else if (generatedCount == quizCount) {
      finalizeSuccess(sessionId, problemSetId, request.quizType(), generatedCount);
    } else {
      finalizePartialSuccess(
          sessionId, problemSetId, request.quizType(), generatedCount, quizCount);
    }
  }

  private ProblemSetGeneratedEvent refineQuiz(
      ProblemSetGeneratedEvent problemSet, QuizType quizType) {
    // 1. 선택지 셔플
    if (quizType == QuizType.MULTIPLE || quizType == QuizType.BLANK) {
      for (var quiz : problemSet.getQuiz()) {
        if (!CollectionUtils.isEmpty(quiz.getSelections())) {
          List<ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI> shuffled =
              new ArrayList<>(quiz.getSelections());
          Collections.shuffle(shuffled);
          quiz.setSelections(shuffled);
        }
      }
    }
    // 2. 마크다운 포맷팅
    for (QuizGeneratedFromAI quiz : problemSet.getQuiz()) {
      quiz.setExplanation(ExplanationMarkdownBuilder.build(quiz));
    }
    return problemSet;
  }

  private void finalizeSuccess(
      String sessionId, Long problemSetId, QuizType quizType, long generatedCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    resultRecorder.recordSuccess(problemSetId, quizType, generatedCount);
  }

  private void finalizePartialSuccess(
      String sessionId, Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    resultRecorder.recordPartialSuccess(problemSetId, quizType, generatedCount, quizCount);
  }

  private void finalizeError(String sessionId, Long problemSetId, String errorMessage) {
    quizCommandService.updateStatus(problemSetId, FAILED);
    notificationService.sendFinishWithError(sessionId, errorMessage);
    resultRecorder.recordError(problemSetId, errorMessage);
  }
}
