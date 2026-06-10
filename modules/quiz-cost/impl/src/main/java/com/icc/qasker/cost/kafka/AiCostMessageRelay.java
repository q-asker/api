package com.icc.qasker.cost.kafka;

import com.icc.qasker.cost.config.AiCostKafkaProperties;
import com.icc.qasker.cost.entity.AiCostOutbox;
import com.icc.qasker.cost.entity.OutboxStatus;
import com.icc.qasker.cost.repository.AiCostOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 폴링 릴레이. 1초 주기로 PENDING 행을 생성 순서대로 조회하여 ai.cost.raw에 발행하고 SENT로 전이한다.
 *
 * <p>발행 실패 행은 PENDING으로 유지되어 다음 폴링에서 재시도된다(at-least-once). 행별로 예외를 격리하여 한 건 실패가 배치 전체를 롤백하지 않게
 * 한다. @EnableScheduling은 QAskerApplication에 이미 선언되어 있다. test 프로파일은 발행을 수행하지 않는다(@Profile("!test")).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AiCostMessageRelay {

  private final AiCostOutboxRepository outboxRepository;
  private final AiCostKafkaProducer producer;
  private final AiCostKafkaProperties properties;
  private final OutboxTracePropagator tracePropagator;

  @Scheduled(fixedDelay = 1000)
  @Transactional
  public void relayPendingEvents() {
    List<AiCostOutbox> pending =
        outboxRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING, PageRequest.of(0, properties.getRelayBatchSize()));

    for (AiCostOutbox outbox : pending) {
      try {
        // 적재 시 저장한 요청 trace 컨텍스트를 복원한 scope에서 발행 →
        // producer span이 원래 요청 trace에 연결되고, traceparent 헤더로 consumer까지 이어진다.
        tracePropagator.runInRestoredContext(
            outbox.getTraceParent(),
            () -> {
              producer.send(outbox.getAggregateKey(), outbox.getPayload());
              // 영속 상태 엔티티의 변경은 트랜잭션 커밋 시점에 flush된다(dirty checking)
              outbox.markSent();
            });
      } catch (Exception e) {
        // PENDING 유지 → 다음 폴링에서 재시도 (at-least-once)
        log.warn("[AI 비용 이벤트 발행 실패] requestId={} — 다음 폴링 재시도", outbox.getRequestId(), e);
      }
    }
  }
}
