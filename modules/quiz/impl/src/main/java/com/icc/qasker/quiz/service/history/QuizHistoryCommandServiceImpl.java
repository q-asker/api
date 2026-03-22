package com.icc.qasker.quiz.service.history;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.QuizHistoryCommandService;
import com.icc.qasker.quiz.converter.UserAnswerConverter;
import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.quiz.repository.QuizHistoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private static final UserAnswerConverter USER_ANSWER_CONVERTER = new UserAnswerConverter();

  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetRepository problemSetRepository;
  private final HashUtil hashUtil;

  @Override
  public String saveHistory(String userId, SaveHistoryRequest request) {
    long id = hashUtil.decode(request.problemSetId());
    problemSetRepository
        .findById(id)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    String answersJson = USER_ANSWER_CONVERTER.convertToDatabaseColumn(request.userAnswers());
    quizHistoryRepository.upsert(userId, id, answersJson, request.score());

    return quizHistoryRepository
        .findIdByProblemSetIdAndUserId(id, userId)
        .map(hashUtil::encode)
        .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
  }

  @Override
  public void deleteHistory(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    problemSetRepository
        .findById(id)
        .filter(ps -> ps.getUserId().equals(userId))
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    int deleted = quizHistoryRepository.deleteByProblemSetIdAndUserId(id, userId);
    if (deleted == 0) {
      throw new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND);
    }
  }
}
