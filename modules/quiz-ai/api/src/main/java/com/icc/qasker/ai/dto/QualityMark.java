package com.icc.qasker.ai.dto;

/**
 * 문항의 품질 판정 결과 전달용 값(생성 게이트 → 저장 경로). status는 QualityStatus
 * 이름("OK"/"BELOW_THRESHOLD"/"UNVERIFIABLE"), feedback은 미달 사유이며 통과 시 null이다. 게이트를 거치지 않은 경우
 * AIProblem.qualityMark는 null이다.
 */
public record QualityMark(String status, String feedback) {}
