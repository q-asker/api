package com.icc.qasker.quizset.entity;

/** 문항 품질 판정 결과. Problem.qualityStatus에 @Enumerated(STRING)으로 매핑된다(DB VARCHAR(20)). */
public enum QualityStatus {
  /** 검증 전 — 생성 직후 미검증 또는 재생성 큐 대기. */
  PENDING,
  /** 통과 — 필수 검증 항목을 모두 통과. */
  OK,
  /** 미달 — 필수 검증 항목 중 하나 이상 실패. */
  BELOW_THRESHOLD,
  /** 검증 불가 — 검증기 오류·시간초과(FR-010). */
  UNVERIFIABLE
}
