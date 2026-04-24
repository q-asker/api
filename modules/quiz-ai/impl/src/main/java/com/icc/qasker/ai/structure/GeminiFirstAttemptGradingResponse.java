package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** ESSAY 1차 시도 채점 AI 응답 구조체. 피드백 없이 요소명 + 점수 + 수행수준만 반환한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiFirstAttemptGradingResponse(
    @JsonPropertyDescription("요소별 채점 결과 목록") List<ElementScore> elementScores,
    @JsonPropertyDescription("획득 총점") int totalScore,
    @JsonPropertyDescription("만점") int maxScore) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ElementScore(
      @JsonPropertyDescription("채점 요소명") String element,
      @JsonPropertyDescription("해당 요소의 만점") int maxPoints,
      @JsonPropertyDescription("획득 점수") int earnedPoints,
      @JsonPropertyDescription("수행 수준: 충족 / 부분 충족 / 미충족") String level) {}
}
