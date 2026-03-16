package com.icc.qasker.ai.dto;

public record AISelectionExplanation(
    // 정답 선택지 전용 (오답이면 null)
    String reasoning,
    String evidence,
    String learningPoint,
    String learningPointReview,

    // 오답 선택지 전용 (정답이면 null)
    String typeLabel,
    String diagnosis,
    String correction,
    String selfCheck,

    // 공통
    String review) {}
