package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Gemini 구조화 응답 — 문제+선택지+해설을 모두 포함. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(
    @JsonPropertyDescription("문제 목록 — 문제+선택지+정답+해설 모두 포함") List<GeminiQuestion> questions) {}
