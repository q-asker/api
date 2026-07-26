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
    @JsonPropertyDescription("사용자 지시 반영 결과") String appliedInstruction,
    @JsonPropertyDescription(
            "빈칸별 허용답안(BLANK/REAL_BLANK 전용). 정답 등장 순서대로 각 빈칸의 허용변형(동의어·이표기·약어·영↔한 표기)을"
                + " 배열로 넣는다. 오답 선택지·상위/하위/인접 개념은 절대 포함하지 않는다. 허용변형이 없으면 빈 배열, 다른 유형은 생략한다.")
        List<List<String>> acceptedAnswers) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(
      @JsonPropertyDescription("선택지 텍스트") String content,
      @JsonPropertyDescription("정답 여부") boolean correct,
      @JsonPropertyDescription("선택지별 해설") String explanation) {}
}
