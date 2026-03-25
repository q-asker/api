package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.ProblemSetReadService;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetReadService problemSetReadService;
  private final HashUtil hashUtil;

  @Override
  public String saveHistory(String userId, SaveHistoryRequest request) {
    long id = hashUtil.decode(request.problemSetId());
    problemSetReadService
        .findProblemSetById(id)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    // 기존 히스토리 전체 삭제 후 재저장 (최신 1건만 유지)
    quizHistoryRepository.deleteAllByProblemSetIdAndUserId(id, userId);

    QuizHistory history =
        QuizHistory.builder()
            .title(request.title())
            .totalTime(request.totalTime())
            .userId(userId)
            .problemSetId(id)
            .answers(request.userAnswers())
            .score(request.score())
            .build();

    QuizHistory saved = quizHistoryRepository.save(history);
    return hashUtil.encode(saved.getId());
  }

  @Override
  public String initHistory(String userId, InitHistoryRequest request) {
    long problemSetId = hashUtil.decode(request.problemSetId());

    // upsert: 있으면 초기화, 없으면 새로 생성 (atomic)
    quizHistoryRepository.upsertInitHistory(userId, problemSetId, request.title());

    QuizHistory history =
        quizHistoryRepository
            .findFirstByProblemSetIdAndUserIdOrderByCreatedAtDesc(problemSetId, userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    return hashUtil.encode(history.getId());
  }

  @Override
  public void updateHistoryTitle(String userId, String historyId, String title) {
    long id = hashUtil.decode(historyId);
    QuizHistory history =
        quizHistoryRepository
            .findById(id)
            .filter(h -> h.getUserId().equals(userId))
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    history.updateTitle(title);
  }

  @Override
  public void deleteAllHistory(String userId) {
    quizHistoryRepository.deleteAllByUserId(userId);
  }

  @Override
  public void deleteHistory(String userId, String historyId) {
    long id = hashUtil.decode(historyId);
    quizHistoryRepository
        .findById(id)
        .filter(h -> h.getUserId().equals(userId))
        .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    quizHistoryRepository.deleteById(id);
  }
}
