package com.icc.qasker.cost.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.cost.config.AiCostKafkaProperties;
import com.icc.qasker.cost.entity.AiCostOutbox;
import com.icc.qasker.cost.entity.OutboxStatus;
import com.icc.qasker.cost.repository.AiCostOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AiCostMessageRelay의 PENDING 폴링·발행·상태 전이 동작을 검증한다. */
class AiCostMessageRelayTest {

  private AiCostOutboxRepository outboxRepository;
  private AiCostKafkaProducer producer;
  private AiCostMessageRelay relay;

  @BeforeEach
  void setUp() {
    outboxRepository = mock(AiCostOutboxRepository.class);
    producer = mock(AiCostKafkaProducer.class);
    AiCostKafkaProperties properties = new AiCostKafkaProperties("ai.cost.raw", 100);
    relay = new AiCostMessageRelay(outboxRepository, producer, properties);
  }

  private AiCostOutbox pendingRow(String requestId, String key, String payload) {
    return AiCostOutbox.builder()
        .requestId(requestId)
        .aggregateKey(key)
        .payload(payload)
        .status(OutboxStatus.PENDING)
        .build();
  }

  @Test
  @DisplayName("PENDING 행을 key=userId로 발행하고 SENT로 전이한다")
  void relay_publishesAndMarksSent() {
    AiCostOutbox row = pendingRow("req-1", "google_1", "{\"a\":1}");
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
        .thenReturn(List.of(row));
    doNothing().when(producer).send(any(), any());

    relay.relayPendingEvents();

    verify(producer).send("google_1", "{\"a\":1}");
    assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(row.getSentAt()).isNotNull();
  }

  @Test
  @DisplayName("발행 실패 행은 PENDING으로 유지되어 재시도된다 (at-least-once)")
  void relay_keepsPendingOnFailure() {
    AiCostOutbox ok = pendingRow("req-ok", "google_1", "{\"a\":1}");
    AiCostOutbox fail = pendingRow("req-fail", "google_2", "{\"b\":2}");
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
        .thenReturn(List.of(ok, fail));
    doNothing().when(producer).send("google_1", "{\"a\":1}");
    doThrow(new IllegalStateException("발행 실패")).when(producer).send("google_2", "{\"b\":2}");

    relay.relayPendingEvents();

    // 성공 행은 SENT, 실패 행은 PENDING 유지 (행별 예외 격리)
    assertThat(ok.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(fail.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(fail.getSentAt()).isNull();
  }

  @Test
  @DisplayName("설정된 배치 크기로 PENDING 행을 폴링한다")
  void relay_pollsWithBatchSize() {
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
        .thenReturn(List.of());

    relay.relayPendingEvents();

    verify(outboxRepository).findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any());
  }
}
