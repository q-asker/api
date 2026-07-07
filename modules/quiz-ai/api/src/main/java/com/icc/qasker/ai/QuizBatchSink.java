package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.AIExplanation;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.RegenerationRecord;

/**
 * 배치 인터리빙 생성(Q9)의 저장 콜백. 오케스트레이터가 Phase 1(문제)·Phase 2(해설)를 배치 단위로 교차 호출한다.
 *
 * <p>구현체(quiz-make)는 문제 저장 시 최종 순번을 부여하고 SSE로 통지하며, 해설 저장 시 저장된 문제를 재로드해 해설만 후속 갱신한다(문항 질문·선지·정답
 * 불변).
 */
public interface QuizBatchSink {

  /**
   * Phase 1: 문제 1건을 저장하고(부여된 세트 내 번호 반환) 즉시 풀이 가능하도록 SSE로 통지한다. 전달되는 {@link AIProblem}의 선지는
   * 오케스트레이터가 이미 최종 순서(셔플/정규화)로 정렬한 상태이므로 구현체는 재정렬하지 않는다. 해설은 이 시점에 비어 있다.
   *
   * @return 저장된 문항에 부여된 세트 내 번호
   */
  int saveProblem(AIProblem problem);

  /** 모든 문제 저장이 끝나 세트가 풀이 가능함을 표시한다(해설 Phase 2는 진행 중일 수 있음). */
  void markProblemsReady();

  /**
   * Phase 2: 문항 1건의 해설을 저장한다. 해설이 스트리밍으로 완성될 때마다 호출되어 단일 문항 read-modify-write가 되며(enhancement lazy
   * + inline dirty tracking 활용), 존재하지 않는 번호는 건너뛴다.
   */
  void saveExplanation(AIExplanation explanation);

  /**
   * 게이트 재생성 전후(v1 미달본 ↔ v2 재생성본)를 비교 로그로 남긴다. 학습자 세트에는 v2만 저장되며(스펙 불변식), 이 기록은 분석 전용이다. 기본 구현은
   * no-op — 로그를 원치 않는 구현체(테스트 등)는 재정의하지 않는다.
   */
  default void logRegeneration(RegenerationRecord record) {}
}
