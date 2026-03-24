package com.icc.qasker.ai.service.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;

/**
 * Gemini API 호출에 대한 Prometheus 메트릭을 통합 기록한다. 비즈니스 로직(QuizOrchestrationServiceImpl)에서 메트릭 관심사를 분리하기
 * 위한 Delegation 패턴.
 */
@Component
public class GeminiMetricsRecorder {

  // Gemini 3 Flash Preview 단가
  private static final double PRICE_INPUT_PER_1M = 0.50;
  private static final double PRICE_CACHE_READ_PER_1M = 0.05;
  private static final double PRICE_OUTPUT_PER_1M = 3.00;
  private static final double PRICE_CACHE_STORAGE_PER_1M_HOUR = 1.00;

  private final MeterRegistry registry;

  // 태그 없는 청크 레벨 메트릭 (청크 단위에서는 maxChunks를 모르므로 태그 없음)
  private final Timer chunkDuration;
  private final Counter tokensInput;
  private final Counter tokensCached;
  private final Counter tokensThinking;
  private final Counter tokensOutput;
  private final Counter costEstimated;
  private final Counter cacheStorageCost;

  public GeminiMetricsRecorder(MeterRegistry registry) {
    this.registry = registry;

    this.chunkDuration =
        Timer.builder("gemini.chunk.duration")
            .description("Gemini API 청크별 응답 시간")
            .register(registry);
    this.tokensInput =
        Counter.builder("gemini.tokens.input").description("Gemini 입력 토큰 (비캐시)").register(registry);
    this.tokensCached =
        Counter.builder("gemini.tokens.cached").description("Gemini 캐시 히트 토큰").register(registry);
    this.tokensThinking =
        Counter.builder("gemini.tokens.thinking")
            .description("Gemini thinking 토큰")
            .register(registry);
    this.tokensOutput =
        Counter.builder("gemini.tokens.output").description("Gemini 출력 토큰").register(registry);
    this.costEstimated =
        Counter.builder("gemini.cost.estimated")
            .description("Gemini 추정 비용 (USD)")
            .register(registry);
    this.cacheStorageCost =
        Counter.builder("gemini.cache.storage.cost")
            .description("Gemini 캐시 저장 추정 비용 (USD)")
            .register(registry);

    Gauge.builder(
            "gemini.cache.hit.ratio",
            this,
            self -> {
              double cached = self.tokensCached.count();
              double input = self.tokensInput.count();
              double total = cached + input;
              return total == 0 ? 0.0 : cached / total;
            })
        .description("Gemini 캐시 토큰 히트율 (0.0~1.0)")
        .register(registry);
  }

  /**
   * 청크 처리 완료 후 메트릭을 기록한다.
   *
   * @param elapsedMs 청크 처리 소요 시간 (밀리초)
   * @param usage ChatResponse에서 추출한 Usage 메타데이터
   * @return 추정 비용 (USD) — 로깅용
   */
  public double recordChunkResult(long elapsedMs, Usage usage) {
    long promptTokens = usage.getPromptTokens();
    long completionTokens = usage.getCompletionTokens();
    long cachedTokens = 0;
    long thoughtsTokens = 0;

    if (usage instanceof GoogleGenAiUsage genAiUsage) {
      cachedTokens =
          genAiUsage.getCachedContentTokenCount() != null
              ? genAiUsage.getCachedContentTokenCount()
              : 0;
      thoughtsTokens =
          genAiUsage.getThoughtsTokenCount() != null ? genAiUsage.getThoughtsTokenCount() : 0;
    }

    long nonCachedInputTokens = Math.max(0, promptTokens - cachedTokens);
    double inputCost = nonCachedInputTokens * PRICE_INPUT_PER_1M / 1_000_000;
    double cacheCost = cachedTokens * PRICE_CACHE_READ_PER_1M / 1_000_000;
    double outputCost = completionTokens * PRICE_OUTPUT_PER_1M / 1_000_000;
    double totalCost = inputCost + cacheCost + outputCost;

    chunkDuration.record(elapsedMs, TimeUnit.MILLISECONDS);
    tokensInput.increment(nonCachedInputTokens);
    tokensCached.increment(cachedTokens);
    tokensThinking.increment(thoughtsTokens);
    tokensOutput.increment(completionTokens);
    costEstimated.increment(totalCost);

    return totalCost;
  }

  /**
   * 요청 단위 응답 시간 메트릭을 기록한다. A/B 테스트용 max_chunks 태그를 동적으로 부여한다.
   *
   * @param maxChunkCount 이 요청에 적용된 최대 청크 수
   * @param requestStartNanos 요청 시작 시각 (System.nanoTime)
   * @param firstQuizNanos 첫 번째 퀴즈 응답 시각, null이면 퀴즈 미생성
   * @param lastQuizNanos 마지막 퀴즈 응답 시각, null이면 퀴즈 미생성
   */
  public void recordRequestDuration(
      int maxChunkCount, long requestStartNanos, Long firstQuizNanos, Long lastQuizNanos) {
    String tag = String.valueOf(maxChunkCount);
    long now = System.nanoTime();

    Timer.builder("gemini.request.total.duration")
        .description("요청 시작 → 전체 퀴즈 생성 완료까지 소요 시간")
        .tag("max_chunks", tag)
        .register(registry)
        .record(now - requestStartNanos, TimeUnit.NANOSECONDS);

    if (firstQuizNanos != null) {
      Timer.builder("gemini.request.first-quiz.duration")
          .description("요청 시작 → 첫 번째 퀴즈 응답까지 소요 시간")
          .tag("max_chunks", tag)
          .register(registry)
          .record(firstQuizNanos - requestStartNanos, TimeUnit.NANOSECONDS);
    }
    if (firstQuizNanos != null && lastQuizNanos != null) {
      Timer.builder("gemini.request.spread.duration")
          .description("첫 번째 퀴즈 응답 ~ 마지막 퀴즈 응답 사이 시간 차이")
          .tag("max_chunks", tag)
          .register(registry)
          .record(lastQuizNanos - firstQuizNanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 캐시 저장 비용을 기록한다. 캐시 삭제 직전에 호출한다.
   *
   * @param tokenCount 캐시에 저장된 토큰 수
   * @param durationMs 캐시 보관 시간 (밀리초)
   */
  public void recordCacheStorageCost(long tokenCount, long durationMs) {
    double durationHours = durationMs / 3_600_000.0;
    double cost = tokenCount * PRICE_CACHE_STORAGE_PER_1M_HOUR / 1_000_000 * durationHours;
    cacheStorageCost.increment(cost);
  }
}
