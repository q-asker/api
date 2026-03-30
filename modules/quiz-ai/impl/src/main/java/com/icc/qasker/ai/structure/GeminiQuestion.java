package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiQuestion(
    @JsonPropertyDescription("서술문 + 질문 1개. 물음표(?)는 마지막 문장에만 허용") String content,
    @JsonPropertyDescription("선택지 목록. 정답과 오답을 모두 포함") List<GeminiSelection> selections,
    @JsonPropertyDescription("문항 전체 해설") String quizExplanation) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(
      @JsonPropertyDescription("답안 텍스트 한 문장") String content,
      @JsonPropertyDescription("정답이면 true, 오답이면 false") boolean correct,
      @JsonPropertyDescription("선택지별 해설") String explanation) {}
}
