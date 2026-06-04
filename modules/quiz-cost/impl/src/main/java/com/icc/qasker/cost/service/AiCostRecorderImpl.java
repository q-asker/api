package com.icc.qasker.cost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.cost.AiCostRecorder;
import com.icc.qasker.cost.dto.AiInvocationCommand;
import com.icc.qasker.cost.entity.AiCostOutbox;
import com.icc.qasker.cost.entity.AiInvocation;
import com.icc.qasker.cost.entity.OutboxStatus;
import com.icc.qasker.cost.event.AiCostEventPayload;
import com.icc.qasker.cost.repository.AiCostOutboxRepository;
import com.icc.qasker.cost.repository.AiInvocationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 호출 토큰 사용량을 원장(ai_invocation)과 Outbox(ai_cost_outbox)에 동일 트랜잭션으로 적재한다. 두 INSERT가 원자 커밋되어
 * dual-write 손실 0을 보장한다(Transactional Outbox). 실제 Kafka 발행은 MessageRelay가 비동기로 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AiCostRecorderImpl implements AiCostRecorder {

  // 발행 메시지(ai.cost.raw) 양식 버전 — 컨슈머의 스키마 진화 대응용
  private static final int SCHEMA_VERSION = 1;

  private final AiInvocationRepository invocationRepository;
  private final AiCostOutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void record(AiInvocationCommand command) {
    // 멱등 키: 호출 측이 부여하지 않았으면 발행 측이 UUID를 생성한다
    String requestId =
        command.requestId() != null ? command.requestId() : UUID.randomUUID().toString();

    // 멱등: 이미 적재된 호출이면 재적재하지 않는다(원장 중복·이중 집계 방지). DB UNIQUE가 최종 방어선.
    if (invocationRepository.existsByRequestId(requestId)) {
      return;
    }

    // 원장 적재
    AiInvocation invocation =
        AiInvocation.builder()
            .requestId(requestId)
            .userId(command.userId())
            .quizSetId(command.quizSetId())
            .model(command.model())
            .modelVersion(command.modelVersion())
            .inputTokens(command.inputTokens())
            .cachedTokens(command.cachedTokens())
            .thinkingTokens(command.thinkingTokens())
            .outputTokens(command.outputTokens())
            .latencyMs(command.latencyMs())
            .status(command.status())
            .errorCode(command.errorCode())
            .occurredAt(command.occurredAt())
            .build();
    invocationRepository.save(invocation);

    // 발행할 이벤트 본문 구성 + 직렬화
    AiCostEventPayload payload =
        new AiCostEventPayload(
            SCHEMA_VERSION,
            requestId,
            command.userId(),
            command.quizSetId(),
            command.model(),
            command.modelVersion(),
            command.inputTokens(),
            command.cachedTokens(),
            command.thinkingTokens(),
            command.outputTokens(),
            command.latencyMs(),
            command.status(),
            command.errorCode(),
            command.occurredAt());

    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      // 직렬화 실패는 런타임 예외로 승격하여 원장 INSERT까지 함께 롤백한다
      throw new IllegalStateException("AI 비용 이벤트 직렬화 실패: requestId=" + requestId, e);
    }

    // Outbox 적재 (PENDING) — 동일 트랜잭션
    AiCostOutbox outbox =
        AiCostOutbox.builder()
            .requestId(requestId)
            .aggregateKey(command.userId())
            .payload(json)
            .status(OutboxStatus.PENDING)
            .build();
    outboxRepository.save(outbox);
  }
}
