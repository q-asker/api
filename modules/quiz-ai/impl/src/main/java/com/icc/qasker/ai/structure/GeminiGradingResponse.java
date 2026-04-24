package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** ESSAY 채점 AI 응답 구조체. 분석적 루브릭 기반 요소별 채점 결과를 담는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiGradingResponse(
    @JsonPropertyDescription("요소별 채점 결과 목록") List<ElementScore> elementScores,
    @JsonPropertyDescription("획득 총점") int totalScore,
    @JsonPropertyDescription("만점") int maxScore,
    @JsonPropertyDescription("종합 피드백 — 잘한 점, 부족한 점, 개선 방향을 포함") String overallFeedback) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ElementScore(
      @JsonPropertyDescription("채점 요소명") String element,
      @JsonPropertyDescription("해당 요소의 만점") int maxPoints,
      @JsonPropertyDescription("획득 점수") int earnedPoints,
      @JsonPropertyDescription("수행 수준: 충족 / 부분 충족 / 미충족") String level,
      @JsonPropertyDescription("해당 요소에 대한 구체적 피드백") String feedback) {}
}
