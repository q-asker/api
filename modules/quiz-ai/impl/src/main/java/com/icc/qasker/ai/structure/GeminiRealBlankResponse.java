package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRealBlankResponse(
    @JsonPropertyDescription("직접 입력 단답 문제 목록 — 문제+모범답안+인정범위+해설 포함")
        List<GeminiRealBlankQuestion> questions) {}
