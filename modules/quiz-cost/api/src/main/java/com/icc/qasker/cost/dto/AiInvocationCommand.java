package com.icc.qasker.cost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * AI 호출 1건의 발행 컨텍스트. quiz-ai(GeminiChatService 호출 측)가 토큰/지연/상태를 채워 AiCostRecorder에 전달한다.
 *
 * <p>토큰 사용량 추적이 목적이므로 비용/통화는 다루지 않는다. requestId는 멱등 키로, 호출 측이 채우거나(권장) null이면 발행 측이 UUID를 생성한다.
 */
public record AiInvocationCommand(
    String requestId,
    @NotBlank String userId,
    Long quizSetId,
    @NotBlank String model,
    String modelVersion,
    long inputTokens,
    long cachedTokens,
    long thinkingTokens,
    long outputTokens,
    long latencyMs,
    @NotNull InvocationStatus status,
    String errorCode,
    @NotNull Instant occurredAt) {}
