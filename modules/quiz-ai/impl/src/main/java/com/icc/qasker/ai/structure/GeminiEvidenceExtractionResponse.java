package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Pass 1 증거 추출 AI 응답 구조체. 루브릭 요소별로 학생 답안에서 인용된 증거를 담는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiEvidenceExtractionResponse(
    @JsonPropertyDescription("루브릭 요소별 증거 추출 결과") List<ElementEvidence> elements) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ElementEvidence(
      @JsonPropertyDescription("채점 요소명 (루브릭에 명시된 그대로)") String element,
      @JsonPropertyDescription("학생 답안에서 이 요소와 관련된 원문 인용. 관련 서술이 없으면 빈 문자열") String quotedEvidence,
      @JsonPropertyDescription("이 요소에서 학생이 언급하지 않은 핵심 개념 목록") List<String> missingConcepts) {}
}
