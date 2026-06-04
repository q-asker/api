package com.icc.qasker.cost.event;

import com.icc.qasker.cost.dto.InvocationStatus;
import java.time.Instant;

/**
 * Kafka 토픽 ai.cost.raw로 발행되는 메시지 본문. AI 호출 1건의 토큰 사용량 원천 데이터를 담는다.
 *
 * <p>후속 컨슈머(dashboard-sink, user-billing-sink)가 의존하는 공개 계약이므로 필드 안정성을 유지한다. requestId는 멱등 키로, 컨슈머는
 * 이를 기준으로 중복(at-least-once 재전송)을 UPSERT로 제거한다. 파티션 key로는 userId를 사용한다. schemaVersion은 메시지 양식 버전으로,
 * 필드 구조가 진화할 때 컨슈머가 버전을 구분하기 위한 값이다(현재 1). 비용/통화는 다루지 않으며, 필요 시 토큰 수로부터 재계산한다.
 */
public record AiCostEventPayload(
    int schemaVersion,
    String requestId,
    String userId,
    Long quizSetId,
    String model,
    String modelVersion,
    long inputTokens,
    long cachedTokens,
    long thinkingTokens,
    long outputTokens,
    long latencyMs,
    InvocationStatus status,
    String errorCode,
    Instant occurredAt) {}
