package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.grading.RealBlankGrader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final QuizFolderRepository quizFolderRepository;
  private final ProblemSetReadService problemSetReadService;
  private final HashUtil hashUtil;

  @Override
  public String initHistory(String userId, InitHistoryRequest request) {

    Long problemSetId = hashUtil.decode(request.problemSetId());
    QuizHistory quizHistory =
        QuizHistory.builder()
            .userId(userId)
            .problemSetId(problemSetId)
            .title(request.title())
            .build();

    try {
      quizHistoryRepository.save(quizHistory);
    } catch (DataIntegrityViolationException e) {
      quizHistory =
          quizHistoryRepository
              .findByUserIdAndProblemSetId(userId, problemSetId)
              .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    }
    return hashUtil.encode(quizHistory.getId());
  }

  @Override
  @Transactional
  public String saveHistory(String userId, SaveHistoryRequest request) {
    Long problemSetId = hashUtil.decode(request.problemSetId());
    List<AnswerSnapshot> snapshots =
        request.userAnswers().stream()
            .map(a -> new AnswerSnapshot(a.number(), a.userAnswer(), a.inReview(), a.textAnswer()))
            .toList();

    int score = resolveScore(problemSetId, request);

    QuizHistory history =
        quizHistoryRepository
            .findByUserIdAndProblemSetId(userId, problemSetId)
            .orElseGet(
                () ->
                    quizHistoryRepository.save(
                        QuizHistory.builder()
                            .userId(userId)
                            .problemSetId(problemSetId)
                            .title(request.title())
                            .build()));
    history.completeQuiz(snapshots, score, request.totalTime());
    return hashUtil.encode(history.getId());
  }

  /**
   * REAL_BLANK 세트는 서버 grader로 맞힌 문항 수를 재계산해 저장한다(클라 score 맹신 폐기, FR-006). 타 유형은 클라가 보낸 score를 그대로
   * 유지한다(채점 동작 불변, FR-007).
   */
  private int resolveScore(Long problemSetId, SaveHistoryRequest request) {
    ProblemSetSummary summary = problemSetReadService.findProblemSetById(problemSetId).orElse(null);
    if (summary == null || summary.quizType() != QuizType.REAL_BLANK) {
      return request.score();
    }
    Map<Integer, String> inputByNumber =
        request.userAnswers().stream()
            .collect(
                Collectors.toMap(
                    a -> a.number(),
                    a -> a.textAnswer() == null ? "" : a.textAnswer(),
                    (a, b) -> a));
    return (int)
        problemSetReadService.findProblemsByProblemSetId(problemSetId).stream()
            .filter(
                p ->
                    RealBlankGrader.grade(p.selections(), inputByNumber.get(p.number()))
                        .isCorrect())
            .count();
  }

  @Override
  @Transactional
  public void updateHistoryTitle(String userId, String historyId, String title) {
    long decodedHistoryId = hashUtil.decode(historyId);
    QuizHistory history =
        quizHistoryRepository
            .findByIdAndUserId(decodedHistoryId, userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    history.updateTitle(title);
  }

  @Override
  @Transactional
  public void assignFolder(String userId, String historyId, String folderId) {
    QuizHistory history =
        quizHistoryRepository
            .findByIdAndUserId(hashUtil.decode(historyId), userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));

    Long targetFolderId = null;
    if (folderId != null) {
      targetFolderId =
          quizFolderRepository
              .findByIdAndUserId(hashUtil.decode(folderId), userId)
              .orElseThrow(() -> new CustomException(ExceptionMessage.FOLDER_NOT_FOUND))
              .getId();
    }
    history.assignFolder(targetFolderId);
  }

  @Override
  @Transactional
  public void deleteAllHistory(String userId) {
    quizHistoryRepository.deleteAllByUserId(userId);
  }

  @Override
  @Transactional
  public void deleteHistory(String userId, String historyId) {
    quizHistoryRepository.deleteByIdAndUserId(hashUtil.decode(historyId), userId);
  }
}
