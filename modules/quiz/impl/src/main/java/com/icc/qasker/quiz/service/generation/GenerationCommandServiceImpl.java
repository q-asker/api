package com.icc.qasker.quiz.service.generation;

import static com.icc.qasker.quiz.GenerationStatus.COMPLETED;
import static com.icc.qasker.quiz.GenerationStatus.FAILED;
import static com.icc.qasker.quiz.GenerationStatus.GENERATING;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationCommandService;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.QuizHistoryCommandService;
import com.icc.qasker.quiz.QuizQueryService;
import com.icc.qasker.quiz.SseNotificationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.mapper.ExplanationMarkdownBuilder;
import com.icc.qasker.quiz.mapper.QuizViewToQuizForFeMapper;
import com.icc.qasker.quiz.view.QuizView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationCommandServiceImpl implements GenerationCommandService {

  // 핵심
  private final AIServerAdapter aiServerAdapter;
  private final SseNotificationService notificationService;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final QuizHistoryCommandService quizHistoryCommandService;
  // 유틸
  private final HashUtil hashUtil;
  private final GenerationSlackNotifier generationSlackNotifier;

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

    try {
      if (userId != null) {
        quizHistoryCommandService.initHistory(userId, problemSetId);
      }
    } catch (Exception e) {
      log.warn("기록 초기화에 실패했습니다 userId: {}, problemSetId: {}", userId, problemSetId, e);
    }

    Thread.ofVirtual()
        .start(() -> processAsyncGeneration(request.sessionId(), problemSetId, request));
  }

  private void processAsyncGeneration(
      String userId, String sessionId, Long problemSetId, GenerationRequest request) {

    AtomicInteger atomicGeneratedCount = new AtomicInteger(0);

    try {
      aiServerAdapter.streamRequest(
          request.uploadedUrl(),
          request.quizType().name(),
          request.quizCount(),
          request.pageNumbers(),
          // 문제 생성시 AI 모듈이 호출할 콜백 함수이다
          (ProblemSetGeneratedEvent problemSet) -> {
            // problemSet.getQuiz() == null || problemSet.getQuiz().isEmpty()
            if (CollectionUtils.isEmpty(problemSet.getQuiz())) {
              log.warn("빈 배치 수신, 건너뜀: sessionId={}", sessionId);
              return;
            }
            postRefineQuiz(problemSet, request.quizType());
            List<Integer> savedNumbers =
                quizCommandService.saveBatch(problemSet.getQuiz(), problemSetId);

            List<QuizView> quizViews = quizQueryService.getQuizViews(problemSetId, savedNumbers);
            if (quizViews.isEmpty()) {
              return;
            }

            List<QuizForFe> quizForFeList =
                quizViews.stream().map(QuizViewToQuizForFeMapper::toQuizForFe).toList();

            atomicGeneratedCount.addAndGet(quizViews.size());

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
          },
          // 2. error 콜백
          ex -> log.error("청크 에러: {}", ex.getMessage()));
    } catch (Exception e) {
      log.error("생성 중 오류 발생: sessionId={}", sessionId, e);
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
      return;
    }

    int generatedCount = atomicGeneratedCount.get();
    if (generatedCount == 0) {
      finalizeError(sessionId, problemSetId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());
    } else if (generatedCount == request.quizCount()) {
      finalizeSuccess(userId, sessionId, problemSetId, request.quizType(), generatedCount);
    } else {
      finalizePartialSuccess(
          userId, sessionId, problemSetId, request.quizType(), generatedCount, request.quizCount());
    }
  }

  private void postRefineQuiz(ProblemSetGeneratedEvent problemSet, QuizType quizType) {
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
  }

  private void finalizeSuccess(
      String sessionId, Long problemSetId, QuizType quizType, long generatedCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    generationSlackNotifier.notifySuccess(problemSetId, quizType, generatedCount);
  }

  private void finalizePartialSuccess(
      String sessionId, Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    notificationService.sendComplete(sessionId);
    generationSlackNotifier.notifyPartialSuccess(problemSetId, quizType, generatedCount, quizCount);
  }

  private void finalizeError(String sessionId, Long problemSetId, String errorMessage) {
    quizCommandService.updateStatus(problemSetId, FAILED);
    notificationService.sendFinishWithError(sessionId, errorMessage);
    generationSlackNotifier.notifyError(problemSetId, errorMessage);
  }
}
