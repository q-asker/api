package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeminiSelection(
    @JsonPropertyDescription("선택지 내용") String content,
    @JsonPropertyDescription("선택지별 해설 (구조화)") GeminiSelectionExplanation explanation,
    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)") boolean correct) {}
