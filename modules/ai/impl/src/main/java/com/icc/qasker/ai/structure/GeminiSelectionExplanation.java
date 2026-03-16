package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeminiSelectionExplanation(
    // === 정답 선택지 전용 (오답이면 null) ===
    @JsonPropertyDescription("정답 추론 — 근거와 추론 경로 설명") String reasoning,
    @JsonPropertyDescription("근거 인용 (예: [강의노트 섹션명] > \"핵심 문장\")") String evidence,
    @JsonPropertyDescription("학습 포인트 — 정답과 주요 오답의 구별 원리 (빈칸형 전용, 다른 타입이면 null)")
        String learningPoint,
    @JsonPropertyDescription("학습 포인트 복습 대상 (빈칸형 전용, 다른 타입이면 null)") String learningPointReview,

    // === 오답 선택지 전용 (정답이면 null) ===
    @JsonPropertyDescription("오답 유형 라벨 (예: 조건 누락형, 오개념형, 사실 변조형 등)") String typeLabel,
    @JsonPropertyDescription("오개념 진단") String diagnosis,
    @JsonPropertyDescription("교정 방향") String correction,
    @JsonPropertyDescription("스스로 점검 — 학습자가 오류를 인식하도록 유도하는 개방형 질문") String selfCheck,

    // === 공통 ===
    @JsonPropertyDescription("복습 참조 (강의노트 섹션명)") String review) {}
