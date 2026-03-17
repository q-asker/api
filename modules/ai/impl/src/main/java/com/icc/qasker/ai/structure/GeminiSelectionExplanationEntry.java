package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 스트리밍 분리 응답용 선택지별 해설 엔트리. */
public record GeminiSelectionExplanationEntry(
    @JsonPropertyDescription("선택지 인덱스 (0부터)") int index,
    @JsonPropertyDescription("선택지 해설 — 정답: **[정답 추론]** 마커 / 오답: [유형] 마커 기반") String explanation) {}
