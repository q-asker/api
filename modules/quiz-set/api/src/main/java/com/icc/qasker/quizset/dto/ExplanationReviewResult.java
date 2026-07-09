package com.icc.qasker.quizset.dto;

/**
 * 해설 형식 검증(정규식) 결과 요약.
 *
 * @param problemSetId 대상 세트 ID
 * @param reviewedCount 검증한 문항 수(품질 로그 보유분)
 * @param violationCount 형식 위반으로 review에 마킹된 문항 수(UPDATE 발생 수)
 */
public record ExplanationReviewResult(Long problemSetId, int reviewedCount, int violationCount) {}
