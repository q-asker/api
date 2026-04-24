package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiEssayResponse(
    @JsonPropertyDescription("서술형 문제 목록 — 문제+모범답안+해설 모두 포함") List<GeminiEssayQuestion> questions) {}
