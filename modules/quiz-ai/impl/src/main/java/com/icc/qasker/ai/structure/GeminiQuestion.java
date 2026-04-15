package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiQuestion(
    @JsonPropertyDescription("질문문") String content,
    @JsonPropertyDescription("선택지 목록") List<GeminiSelection> selections,
    @JsonPropertyDescription("문항 전체 해설") String quizExplanation,
    @JsonPropertyDescription("참조한 강의노트 페이지 번호") List<Integer> referencedPages) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(
      @JsonPropertyDescription("선택지 텍스트") String content,
      @JsonPropertyDescription("정답 여부") boolean correct,
      @JsonPropertyDescription("선택지별 해설") String explanation) {}
}
