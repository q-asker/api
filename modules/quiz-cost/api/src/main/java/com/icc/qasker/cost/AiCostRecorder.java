package com.icc.qasker.cost;

import com.icc.qasker.cost.dto.AiInvocationCommand;

/**
 * AI 호출 비용을 영구 적재하는 발행 측 진입점. 구현체(quiz-cost-impl)는 ai_invocation 원장과 ai_cost_outbox를 동일 트랜잭션으로
 * INSERT하여 dual-write 손실 0을 보장한다. 실제 Kafka 발행은 MessageRelay가 비동기로 담당한다.
 *
 * <p>quiz-ai 등 호출 측은 이 인터페이스에만 의존한다(impl→api 의존 방향 준수).
 */
public interface AiCostRecorder {

  /**
   * AI 호출 1건을 원장 + Outbox에 적재한다. 성공/실패(command.status) 모두 적재 대상이다.
   *
   * @param command 호출 컨텍스트(사용자·토큰·지연·상태 등)
   */
  void record(AiInvocationCommand command);
}
