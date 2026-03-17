package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** 스트리밍 분리 응답용 문항별 해설 엔트리. */
public record GeminiExplanationEntry(
    @JsonPropertyDescription("문항 번호 (questions와 매칭)") int number,
    @JsonPropertyDescription("문항 전체 해설 — 마커: **[자기 점검]** (라벨) + **[심화 학습]**")
        String quizExplanation,
    @JsonPropertyDescription("선택지별 해설 목록")
        List<GeminiSelectionExplanationEntry> selectionExplanations) {}
