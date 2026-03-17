package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 스트리밍 분리 응답 — questions를 먼저 선언하여 Gemini가 문제를 먼저 생성하도록 한다.
 *
 * <p>Gemini 공식: "Model generates responses in the same order as schema keys"
 */
public record GeminiSplitResponse(
    @JsonPropertyDescription("문제 목록 — 해설 없이 문제+선택지+정답만") List<GeminiQuestionEntry> questions,
    @JsonPropertyDescription("해설 목록 — 문항 번호로 매칭") List<GeminiExplanationEntry> explanations) {}
