package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiQuestion(
    @JsonPropertyDescription("질문문") String content,
    @JsonPropertyDescription("선택지 목록") List<GeminiSelection> selections,
    @JsonPropertyDescription("이 문항에 적용된 Bloom's 수준") String bloomsLevel,
    @JsonPropertyDescription("참조한 강의노트 페이지 번호") List<Integer> referencedPages,
    @JsonPropertyDescription("사용자 지시 반영 결과") String appliedInstruction) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(
      @JsonPropertyDescription("선택지 텍스트") String content,
      @JsonPropertyDescription("정답 여부") boolean correct,
      @JsonPropertyDescription("선택지별 해설") String explanation) {}
}
