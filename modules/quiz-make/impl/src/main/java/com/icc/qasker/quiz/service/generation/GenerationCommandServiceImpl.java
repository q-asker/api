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
import com.icc.qasker.quiz.view.QuizView;
import com.icc.qasker.quiz.view.QuizViewToQuizForFeMapper;
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

  private final AIServerAdapter aiServerAdapter;
  private final SseNotificationService notificationService;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final HashUtil hashUtil;
  private final GenerationResultRecorder resultRecorder;

  public GenerationCommandServiceImpl(
      AIServerAdapter aiServerAdapter,
      SseNotificationService notificationService,
      QuizCommandService quizCommandService,
      QuizQueryService quizQueryService,
      HashUtil hashUtil,
      GenerationResultRecorder resultRecorder) {
    this.aiServerAdapter = aiServerAdapter;
    this.notificationService = notificationService;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.hashUtil = hashUtil;
    this.resultRecorder = resultRecorder;
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

    AtomicInteger atomicGeneratedCount = new AtomicInteger(0);

    GenerationRequestToAI requestToAI =
        GenerationRequestToAI.builder()
            .fileUrl(request.uploadedUrl())
            .strategyValue(request.quizType().name())
            .language(request.language().name())
            .quizCount(request.quizCount())
            .referencePages(request.pageNumbers())
            .questionsConsumer(
                aiProblemSet -> {
                  // 1. 전송용 DTO로 변환
                  ProblemSetGeneratedEvent problemSet = AIProblemSetMapper.toEvent(aiProblemSet);
                  if (CollectionUtils.isEmpty(problemSet.getQuiz())) {
                    log.warn("빈 배치 수신, 건너뜀: sessionId={}", sessionId);
                    return;
                  }

                  // 2. 선택지 셔플
                  QuizType quizType = request.quizType();
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

                  // 3. 마크다운 포맷팅
                  for (QuizGeneratedFromAI quiz : problemSet.getQuiz()) {
                    quiz.setExplanation(ExplanationMarkdownBuilder.build(quiz, request.language()));
                  }

                  // 4. 데이터베이스에 영속화
                  List<Integer> savedNumbers =
                      quizCommandService.saveBatch(problemSet.getQuiz(), problemSetId);

                  // 5. 데이터베이스에 영속화
                  List<QuizView> quizViews =
                      quizQueryService.getQuizViews(problemSetId, savedNumbers);
                  if (quizViews.isEmpty()) {
                    return;
                  }

                  //
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
                          request.quizType(),
                          request.quizCount(),
                          quizForFeList));

                  atomicGeneratedCount.addAndGet(quizViews.size());
                })
            .build();

    int maxChunkCount;
    try {
      maxChunkCount = aiServerAdapter.streamRequest(requestToAI);
    } catch (Exception e) {
      log.error("생성 중 오류 발생: sessionId={}", sessionId, e);
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
      return;
    }

    int generatedCount = atomicGeneratedCount.get();
    int quizCount = request.quizCount();

    // 요청/생성/실패 문제 수 메트릭 기록 (finalize 결과와 무관하게 항상 실행)
    resultRecorder.recordQuizCounts(request.quizType(), quizCount, generatedCount);

    if (generatedCount == 0) {
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
    } else if (generatedCount == quizCount) {
      finalizeSuccess(sessionId, problemSetId, request.quizType(), generatedCount);
    } else {
      finalizePartialSuccess(
          sessionId, problemSetId, request.quizType(), generatedCount, quizCount);
    }
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
