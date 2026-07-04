package com.icc.qasker.quizhistory.mapper;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.ElementScoreDetail;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.EssayProblemWithGrade;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.EssaySelection;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.GradeResult;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quizhistory.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizset.dto.feresponse.Selection;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuizHistoryMapper {

  private final HashUtil hashUtil;

  /** QuizHistory + ProblemSetSummary → HistorySummaryResponse 변환 */
  public HistorySummaryResponse toSummary(QuizHistory history, ProblemSetSummary problemSet) {
    boolean completed =
        switch (history.getStatus()) {
          case COMPLETED -> true;
          case INCOMPLETE -> false;
        };
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

  /** ProblemDetail + 답안 스냅샷 → ProblemWithAnswer(객관식 상세) 변환. 정답 인덱스와 사용자 답을 비교해 정오답을 판정한다. */
  public ProblemWithAnswer toProblemWithAnswer(ProblemDetail problem, AnswerSnapshotView answers) {
    List<SelectionDetail> rawSelections = problem.selections();
    int correctIndex = findCorrectIndex(rawSelections);
    int userAnswer = answers.userAnswer(problem.number());
    boolean correct = userAnswer == correctIndex;
    List<Selection> selections =
        IntStream.range(0, rawSelections.size())
            .mapToObj(
                i ->
                    new Selection(
                        i + 1, rawSelections.get(i).content(), rawSelections.get(i).correct()))
            .toList();
    return new ProblemWithAnswer(
        problem.number(),
        problem.title(),
        userAnswer,
        correct,
        answers.inReview(problem.number()),
        selections,
        answers.textAnswer(problem.number()));
  }

  /** ProblemDetail + 답안 스냅샷 + 최신 채점 로그 → EssayProblemWithGrade(서술형 상세) 변환. */
  public EssayProblemWithGrade toEssayProblemWithGrade(
      ProblemDetail problem, AnswerSnapshotView answers, EssayGradeLog gradeLog) {
    List<EssaySelection> selections =
        IntStream.range(0, problem.selections().size())
            .mapToObj(i -> new EssaySelection(i + 1, problem.selections().get(i).content()))
            .toList();
    return new EssayProblemWithGrade(
        problem.number(),
        problem.title(),
        answers.textAnswer(problem.number()),
        answers.inReview(problem.number()),
        selections,
        toGradeResult(gradeLog));
  }

  private GradeResult toGradeResult(EssayGradeLog gradeLog) {
    if (gradeLog == null) {
      return null;
    }
    List<ElementScoreDetail> elementScores =
        gradeLog.getElementScores().stream()
            .map(
                e ->
                    new ElementScoreDetail(
                        e.element(), e.maxPoints(), e.earnedPoints(), e.level(), e.feedback()))
            .toList();
    return new GradeResult(
        gradeLog.getTotalScore(),
        gradeLog.getMaxScore(),
        gradeLog.getOverallFeedback(),
        elementScores);
  }

  private int findCorrectIndex(List<SelectionDetail> selections) {
    for (int i = 0; i < selections.size(); i++) {
      if (selections.get(i).correct()) {
        return i + 1;
      }
    }
    return -1;
  }
}
