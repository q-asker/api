package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 스트리밍 분리 응답용 선택지 (해설 없음). */
public record GeminiQuestionSelection(
    @JsonPropertyDescription("선택지 내용") String content,
    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)") boolean correct) {}
