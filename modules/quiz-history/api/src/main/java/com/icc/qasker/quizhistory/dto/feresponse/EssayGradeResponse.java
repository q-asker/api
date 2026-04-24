package com.icc.qasker.quizhistory.dto.feresponse;

import java.util.List;

/** ESSAY 채점 결과 응답 DTO. */
public record EssayGradeResponse(
    List<ElementScoreResponse> elementScores,
    int totalScore,
    int maxScore,
    String overallFeedback) {

  public record ElementScoreResponse(
      String element, int maxPoints, int earnedPoints, String level, String feedback) {}
}
