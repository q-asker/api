package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** ESSAY 채점 결과 DTO. AI 서비스 결과·FE 응답·DB 스냅샷을 단일 record로 통일한다. */
public record EssayGradingResult(
    List<ElementScore> elementScores,
    int totalScore,
    int maxScore,
    String overallFeedback,
    @JsonIgnore String evidenceJson) {

  public record ElementScore(
      String element, int maxPoints, int earnedPoints, String level, String feedback) {}
}
