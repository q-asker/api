package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeminiSelection(
    @JsonPropertyDescription("선택지 텍스트")
    String content,

    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)")
    boolean correct
) {

}
