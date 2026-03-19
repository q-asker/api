package com.icc.qasker.quiz.service.history;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.QuizHistoryCommandService;
import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quiz.entity.QuizHistory;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.quiz.repository.QuizHistoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetRepository problemSetRepository;
  private final HashUtil hashUtil;

  @Override
  public String saveHistory(String userId, SaveHistoryRequest request) {
    long id = hashUtil.decode(request.problemSetId());
    problemSetRepository
        .findById(id)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    // 기존 히스토리 전체 삭제 후 재저장 (최신 1건만 유지)
    quizHistoryRepository.deleteAllByProblemSetIdAndUserId(id, userId);

    QuizHistory history =
        QuizHistory.builder()
            .userId(userId)
            .problemSetId(id)
            .answers(request.userAnswers())
            .score(request.score())
            .build();

    QuizHistory saved = quizHistoryRepository.save(history);
    return hashUtil.encode(saved.getId());
  }

  @Override
  public void deleteHistory(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    problemSetRepository
        .findById(id)
        .filter(ps -> ps.getUserId().equals(userId))
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    // 미완료(히스토리 없음)는 삭제 불가
    int updated = quizHistoryRepository.softDeleteByProblemSetIdAndUserId(id, userId);
    if (updated == 0) {
      throw new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND);
    }
  }
}
