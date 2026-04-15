package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private final QuizHistoryRepository quizHistoryRepository;
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
    List<AnswerSnapshot> snapshots =
        request.userAnswers().stream()
            .map(a -> new AnswerSnapshot(a.number(), a.userAnswer(), a.inReview()))
            .toList();

    QuizHistory history =
        quizHistoryRepository
            .findByUserIdAndProblemSetId(userId, hashUtil.decode(request.problemSetId()))
            .orElseGet(
                () ->
                    quizHistoryRepository.save(
                        QuizHistory.builder()
                            .userId(userId)
                            .problemSetId(hashUtil.decode(request.problemSetId()))
                            .title(request.title())
                            .build()));
    history.completeQuiz(snapshots, request.score(), request.totalTime());
    return hashUtil.encode(history.getId());
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
  public void deleteAllHistory(String userId) {
    quizHistoryRepository.deleteAllByUserId(userId);
  }

  @Override
  @Transactional
  public void deleteHistory(String userId, String historyId) {
    quizHistoryRepository.deleteByIdAndUserId(hashUtil.decode(historyId), userId);
  }
}
