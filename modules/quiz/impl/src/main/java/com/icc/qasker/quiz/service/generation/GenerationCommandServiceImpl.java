package com.icc.qasker.quiz.service.generation;

import static com.icc.qasker.quiz.GenerationStatus.COMPLETED;
import static com.icc.qasker.quiz.GenerationStatus.FAILED;
import static com.icc.qasker.quiz.GenerationStatus.GENERATING;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.QAskerProperties;
import com.icc.qasker.quiz.GenerationCommandService;
import com.icc.qasker.quiz.GenerationStatus;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
  private final SlackNotifier slackNotifier;
  private final QAskerProperties qAskerProperties;

  @Override
  public void triggerGeneration(String userId, GenerationRequest request) {
    // TOC
    Optional<GenerationStatus> status =
        quizQueryService.getGenerationStatusBySessionId(request.sessionId());
    if (status.isPresent()) {
      log.info("중복 요청 발생: sessionId: {}", request.sessionId());
      throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
    }

    // TOU
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
      log.info("제약 조건 위반: sessionId={}", request.sessionId(), e);
      throw new CustomException(ExceptionMessage.AI_DUPLICATED_GENERATION);
    }

    Thread.ofVirtual()
        .uncaughtExceptionHandler(
            (t, e) -> {
              log.error("가상 스레드 미처리 예외 발생: sessionId={}", request.sessionId(), e);
            })
        .start(() -> processAsyncGeneration(userId, request.sessionId(), problemSetId, request));
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
          // 1. questions 콜백: 셔플 → 마크다운 빌드 → saveBatch + SSE
          (ProblemSetGeneratedEvent problemSet) -> {
            if (problemSet.getQuiz() == null || problemSet.getQuiz().isEmpty()) {
              log.warn("빈 배치 수신, 건너뜀: sessionId={}", sessionId);
              return;
            }
            shuffleSelectionsIfNeeded(problemSet, request.quizType());
            buildExplanations(problemSet);
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
          ex -> {
            log.error("청크 에러: {}", ex.getMessage());
          });
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

  private void finalizeSuccess(
      String userId, String sessionId, Long problemSetId, QuizType quizType, long generatedCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    quizHistoryCommandService.createHistory(userId, problemSetId);
    notificationService.sendComplete(sessionId);
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = qAskerProperties.getFrontendDeployUrl() + "/quiz/" + encodedId;
    slackNotifier.asyncNotifyText(
        """
        ✅ [퀴즈 생성 완료 알림]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        문제 수: %d
        """
            .formatted(quizUrl, encodedId, quizType, generatedCount));
  }

  private void finalizePartialSuccess(
      String userId,
      String sessionId,
      Long problemSetId,
      QuizType quizType,
      long generatedCount,
      long quizCount) {
    quizCommandService.updateStatus(problemSetId, COMPLETED);
    quizHistoryCommandService.createHistory(userId, problemSetId);
    notificationService.sendComplete(sessionId);
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = qAskerProperties.getFrontendDeployUrl() + "/quiz/" + encodedId;
    slackNotifier.asyncNotifyText(
        """
        ⚠️ [퀴즈 생성 부분 완료]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        생성된 문제 수: %d개 / 총 문제 수: %d개
        """
            .formatted(quizUrl, encodedId, quizType, generatedCount, quizCount));
  }

  private void finalizeError(String sessionId, Long problemSetId, String errorMessage) {
    quizCommandService.updateStatus(problemSetId, FAILED);
    notificationService.sendFinishWithError(sessionId, errorMessage);
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = qAskerProperties.getFrontendDeployUrl() + "/quiz/" + encodedId;
    slackNotifier.asyncNotifyText(
        """
        ❌ [퀴즈 생성 실패]
        ProblemSetId: <%s|%s>
        원인: %s
        """
            .formatted(quizUrl, encodedId, errorMessage));
  }

  private void shuffleSelectionsIfNeeded(ProblemSetGeneratedEvent problemSet, QuizType quizType) {
    if (quizType != QuizType.MULTIPLE && quizType != QuizType.BLANK) {
      return;
    }
    for (var quiz : problemSet.getQuiz()) {
      if (quiz.getSelections() != null && !quiz.getSelections().isEmpty()) {
        List<ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI> shuffled =
            new ArrayList<>(quiz.getSelections());
        Collections.shuffle(shuffled);
        quiz.setSelections(shuffled);
      }
    }
  }

  /** 셔플된 선택지 순서에 맞춰 해설 마크다운을 빌드하여 explanation에 설정한다. */
  private void buildExplanations(ProblemSetGeneratedEvent problemSet) {
    for (QuizGeneratedFromAI quiz : problemSet.getQuiz()) {
      quiz.setExplanation(ExplanationMarkdownBuilder.build(quiz));
    }
  }
}
