package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AISelection(
    @JsonPropertyDescription("선택지 텍스트 내용입니다.")
    String content,

    @JsonPropertyDescription("정답 여부입니다. (정답: true, 오답: false)")
    boolean correct
) {

}