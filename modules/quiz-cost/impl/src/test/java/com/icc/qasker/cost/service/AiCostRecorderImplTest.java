package com.icc.qasker.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icc.qasker.cost.dto.AiInvocationCommand;
import com.icc.qasker.cost.dto.InvocationStatus;
import com.icc.qasker.cost.entity.AiCostOutbox;
import com.icc.qasker.cost.entity.AiInvocation;
import com.icc.qasker.cost.entity.OutboxStatus;
import com.icc.qasker.cost.kafka.OutboxTracePropagator;
import com.icc.qasker.cost.repository.AiCostOutboxRepository;
import com.icc.qasker.cost.repository.AiInvocationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AiCostRecorderImpl의 원장+Outbox 동일 트랜잭션 적재 동작을 검증한다. */
class AiCostRecorderImplTest {

  private static final String MODEL = "gemini-3-flash-preview";
  private static final String USER_ID = "google_12345";

  private AiInvocationRepository invocationRepository;
  private AiCostOutboxRepository outboxRepository;
  private ObjectMapper objectMapper;
  private AiCostRecorderImpl recorder;

  @BeforeEach
  void setUp() {
    invocationRepository = mock(AiInvocationRepository.class);
    outboxRepository = mock(AiCostOutboxRepository.class);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    recorder =
        new AiCostRecorderImpl(
            invocationRepository, outboxRepository, objectMapper, noopPropagator());
  }

  // 추적 비활성(Tracer/Propagator 부재) no-op 인스턴스 — capture()는 null 반환
  private OutboxTracePropagator noopPropagator() {
    return new OutboxTracePropagator(Optional.empty(), Optional.empty());
  }

  private AiInvocationCommand command(String requestId) {
    return new AiInvocationCommand(
        requestId,
        USER_ID,
        100L,
        MODEL,
        "2026-05",
        1_000_000,
        0,
        0,
        0,
        4200L,
        InvocationStatus.SUCCESS,
        null,
        Instant.parse("2026-05-31T00:00:00Z"));
  }

  @Test
  @DisplayName("호출 1건을 원장 1행 + Outbox 1행(PENDING)으로 적재한다")
  void record_persistsInvocationAndOutbox() {
    recorder.record(command(null));

    ArgumentCaptor<AiInvocation> invCaptor = ArgumentCaptor.forClass(AiInvocation.class);
    ArgumentCaptor<AiCostOutbox> outboxCaptor = ArgumentCaptor.forClass(AiCostOutbox.class);
    verify(invocationRepository).save(invCaptor.capture());
    verify(outboxRepository).save(outboxCaptor.capture());

    AiInvocation inv = invCaptor.getValue();
    AiCostOutbox outbox = outboxCaptor.getValue();

    // 멱등 키가 생성되고 원장·Outbox에 일관되게 들어간다
    assertThat(inv.getRequestId()).isNotBlank();
    assertThat(outbox.getRequestId()).isEqualTo(inv.getRequestId());
    // 토큰 사용량이 그대로 적재된다
    assertThat(inv.getInputTokens()).isEqualTo(1_000_000);
    // Outbox는 PENDING, 파티션 key는 userId
    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(outbox.getAggregateKey()).isEqualTo(USER_ID);
    // payload에 멱등 키·사용자 식별자가 직렬화되어 있다
    assertThat(outbox.getPayload()).contains(inv.getRequestId()).contains(USER_ID);
  }

  @Test
  @DisplayName("호출 측이 부여한 requestId를 그대로 사용한다")
  void record_usesProvidedRequestId() {
    String fixed = "11111111-1111-1111-1111-111111111111";

    recorder.record(command(fixed));

    ArgumentCaptor<AiInvocation> invCaptor = ArgumentCaptor.forClass(AiInvocation.class);
    verify(invocationRepository).save(invCaptor.capture());
    assertThat(invCaptor.getValue().getRequestId()).isEqualTo(fixed);
  }

  @Test
  @DisplayName("직렬화 실패 시 예외를 던지고 Outbox를 저장하지 않는다 (TX 롤백)")
  void record_serializationFailure_throwsAndSkipsOutbox() throws JsonProcessingException {
    ObjectMapper failing = mock(ObjectMapper.class);
    when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
    AiCostRecorderImpl failingRecorder =
        new AiCostRecorderImpl(invocationRepository, outboxRepository, failing, noopPropagator());

    assertThatThrownBy(() -> failingRecorder.record(command(null)))
        .isInstanceOf(IllegalStateException.class);

    // 직렬화 이전에 호출된 원장 save는 @Transactional로 함께 롤백되며, Outbox는 저장되지 않는다
    verify(outboxRepository, never()).save(any());
  }
}
