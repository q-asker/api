package com.icc.qasker.quizset.dto;

/**
 * 승인 기반 품질 재검토(Pass 2) 결과 요약.
 *
 * @param problemSetId 대상 세트 ID
 * @param reviewedCount 재검증한 문항 수(품질 로그 보유분)
 * @param belowThresholdCount 미달로 마킹된 문항 수(UPDATE 발생 수)
 * @param status 처리 상태("COMPLETED" 등)
 */
public record QualityReviewResult(
    Long problemSetId, int reviewedCount, int belowThresholdCount, String status) {}
