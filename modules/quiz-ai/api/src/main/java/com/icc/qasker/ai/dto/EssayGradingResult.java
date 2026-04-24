package com.icc.qasker.ai.dto;

import java.util.List;

/** ESSAY 채점 결과 DTO. quiz-ai 모듈 경계를 넘어 채점 결과를 전달한다. */
public record EssayGradingResult(
    List<ElementScore> elementScores,
    int totalScore,
    int maxScore,
    String overallFeedback,
    String evidenceJson) {

  public record ElementScore(
      String element, int maxPoints, int earnedPoints, String level, String feedback) {}
}
