package com.icc.qasker.quiz.mapper;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quiz.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.QuizHistory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuizHistoryMapper {

  private final HashUtil hashUtil;

  /** QuizHistory + ProblemSet → HistorySummaryResponse 변환 */
  public HistorySummaryResponse toSummary(QuizHistory history, ProblemSet problemSet) {
    boolean completed = history.getAnswers() != null;
    return new HistorySummaryResponse(
        hashUtil.encode(problemSet.getId()),
        history.getTitle(),
        problemSet.getCreatedAt(),
        hashUtil.encode(history.getId()),
        problemSet.getQuizType(),
        problemSet.getTotalQuizCount(),
        completed,
        history.getScore(),
        history.getCreatedAt());
  }
}
