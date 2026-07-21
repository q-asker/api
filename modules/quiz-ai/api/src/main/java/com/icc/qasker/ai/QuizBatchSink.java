package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.AIProblem;

/**
 * 배치 인터리빙 생성(Q9)의 저장 콜백. 오케스트레이터가 문항을 배치 단위로 저장하고, 품질 로그(v1/v2)를 명시적으로 기록한다.
 *
 * <p>구현체(quiz-make)는 문제 저장 시 최종 순번을 부여하고 SSE로 통지한다. 품질 로그 기록({@link #recordV1}/{@link #recordV2})은
 * 서빙(problem)과 분리된 best-effort 기록으로, 실패해도 저장 흐름을 막지 않는다.
 */
public interface QuizBatchSink {

  /**
   * Phase 1: 문제 1건을 저장하고(부여된 세트 내 번호 반환) 즉시 풀이 가능하도록 SSE로 통지한다. 전달되는 {@link AIProblem}의 선지는
   * 오케스트레이터가 이미 최종 순서(셔플/정규화)로 정렬한 상태이므로 구현체는 재정렬하지 않는다.
   *
   * @return 저장된 문항에 부여된 세트 내 번호
   */
  int saveProblem(AIProblem problem);

  /**
   * 첫 생성본(v1)을 품질 로그에 기록한다. 통과 문항은 v1Feedback=null, 보류→재생성된 문항은 미달 원본과 그 사유를 담는다. 기본 구현은 no-op —
   * 로그를 원치 않는 구현체(테스트 등)는 재정의하지 않는다.
   */
  default void recordV1(int number, AIProblem v1, String v1Feedback) {}

  /** 재생성된 개선본(v2)을 해당 문항의 품질 로그 행에 부착한다. 기본 구현은 no-op. */
  default void recordV2(int number, AIProblem v2) {}
}
