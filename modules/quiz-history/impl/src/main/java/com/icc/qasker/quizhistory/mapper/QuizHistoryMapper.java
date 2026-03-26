package com.icc.qasker.quizhistory.mapper;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quiz.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuizHistoryMapper {

  private final HashUtil hashUtil;

  /** QuizHistory + ProblemSetSummary → HistorySummaryResponse 변환 */
  public HistorySummaryResponse toSummary(QuizHistory history, ProblemSetSummary problemSet) {
    boolean completed = !history.getAnswers().isEmpty();
    return new HistorySummaryResponse(
        hashUtil.encode(problemSet.id()),
        history.getTitle(),
        problemSet.createdAt(),
        hashUtil.encode(history.getId()),
        problemSet.quizType(),
        problemSet.totalQuizCount(),
        completed,
        history.getScore(),
        history.getCreatedAt());
  }
}
