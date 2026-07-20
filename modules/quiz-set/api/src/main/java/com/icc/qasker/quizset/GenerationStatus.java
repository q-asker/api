package com.icc.qasker.quizset;

public enum GenerationStatus {
  FAILED,
  GENERATING,
  // Phase 1(문제·선지) 저장 완료 — 풀이 시작 가능, 해설(Phase 2)은 진행 중
  PROBLEMS_READY,
  COMPLETED;

  /**
   * FE 노출용 상태로 번역한다. PROBLEMS_READY는 내부 전용 상태이며, 현행 FE의 상태 분기(COMPLETED/GENERATING)에 fallback이 없어
   * 미지 값 유입 시 로딩이 멈추므로 COMPLETED로 번역한다. 나머지는 그대로 노출한다.
   */
  public GenerationStatus toClientVisible() {
    return this == PROBLEMS_READY ? COMPLETED : this;
  }
}
