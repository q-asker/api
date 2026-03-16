package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeminiQuizExplanation(
    @JsonPropertyDescription("자기 점검 라벨 (예: '중: 유사 개념 비교', '기억: 유사 개념 비교')") String selfCheckLabel,
    @JsonPropertyDescription("자기 점검 메타인지 질문") String selfCheckContent,
    @JsonPropertyDescription("심화 학습 내용 — 복습 범위 + 다음 Bloom's 인지 수준 학습 경로")
        String deepLearningContent) {}
