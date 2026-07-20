package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 품질 검증 응답 구조. 이진 판정(passed)과 미달 시 개선 피드백(feedback)만 산출한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiVerificationResponse(
    @JsonPropertyDescription("모든 필수 검증 항목을 통과하면 true, 하나라도 실패하면 false") boolean passed,
    @JsonPropertyDescription("미달 시 실패한 항목과 개선 방향을 담은 구체적 피드백(통과 시 빈 문자열)") String feedback) {}
