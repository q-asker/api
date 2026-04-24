package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.time.Instant;
import java.util.List;

/** ESSAY 히스토리 상세 응답 DTO. 문제별 최신 채점 결과를 포함한다. */
public record EssayHistoryDetailResponse(
    String historyId,
    String problemSetId,
    QuizType quizType,
    int totalCount,
    String totalTime,
    Instant takenAt,
    List<EssayProblemWithGrade> problems) {

  public record EssayProblemWithGrade(
      int number,
      String title,
      String textAnswer,
      boolean inReview,
      List<EssaySelection> selections,
      GradeResult gradeResult) {}

  public record EssaySelection(int id, String content) {}

  public record GradeResult(
      int totalScore,
      int maxScore,
      String overallFeedback,
      List<ElementScoreDetail> elementScores) {}

  public record ElementScoreDetail(
      String element, int maxPoints, int earnedPoints, String level, String feedback) {}
}
