package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** 스트리밍 분리 응답용 문제 엔트리. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiQuestion(
    @JsonPropertyDescription("문제 내용") String content,
    @JsonPropertyDescription("선택지 목록") List<GeminiSelection> selections,
    @JsonPropertyDescription("문항 전체 해설 — 마커: **[자기 점검]** (라벨) + **[심화 학습]**")
        String quizExplanation) {

  /** 스트리밍 분리 응답용 선택지. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(
      @JsonPropertyDescription(
              "학습자에게 표시할 답안 텍스트만 작성."
                  + " [유형], 진단, 교정, 스스로 점검, 복습 등 해설 요소는 절대 포함 금지"
                  + " — 해설은 explanation 필드에 작성.")
          String content,
      @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)") boolean correct,
      @JsonPropertyDescription(
              "선택지 해설 본문 — 정답: **[정답 추론]** 마커로 시작 / 오답: [유형] 마커로 시작."
                  + " 헤더(## 정답/오답 선택지)와 선택지 내용은 포함하지 마세요.")
          String explanation) {}
}
