package com.icc.qasker.cost.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 비용 이벤트 Transactional Outbox. AiInvocation 적재와 동일 트랜잭션으로 INSERT되어 dual-write 손실 0을 보장한다.
 * MessageRelay가 PENDING 행을 폴링·발행 후 markSent()로 SENT 전이한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "ai_cost_outbox")
public class AiCostOutbox extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 대응 ai_invocation 의 멱등 키 */
  @Column(nullable = false, columnDefinition = "CHAR(36)")
  private String requestId;

  /** Kafka 파티션 key 원천 (userId) */
  @Column(nullable = false)
  private String aggregateKey;

  /** 발행할 AiCostEventPayload 의 JSON 직렬화 본문 */
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String payload;

  /** 발행 시 복원할 분산 추적 컨텍스트(W3C traceparent). 추적 비활성이면 null. */
  @Column(length = 64)
  private String traceParent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  /** 발행 완료 시각 (미발행 시 null) */
  private Instant sentAt;

  /** 발행 성공 후 SENT로 전이한다. */
  public void markSent() {
    this.status = OutboxStatus.SENT;
    this.sentAt = Instant.now();
  }
}
