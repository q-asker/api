package com.icc.qasker.quizset.scheduler;

/**
 * 방치된 ProblemSet 정리 진입점. 실 구현({@link StaleGenerationRecoveryService})은 스테일 세트를 삭제하고, mock 프로파일 구현은
 * 측정 대상 SELECT 만 실행해 순증 0 으로 태운다(부하 하네스에서 스케줄러가 파괴적 deleteAll 로 시드를 갉아먹지 않도록).
 */
public interface StaleGenerationRecovery {

  /** FAILED 또는 10분 이상 GENERATING 상태로 방치된 ProblemSet을 정리하고 대상 건수를 반환한다. */
  int purgeStaleProblemSets();
}
