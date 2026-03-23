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

  private final Timer chunkDuration;
  private final Timer firstQuizDuration;
  private final Timer totalRequestDuration;
  private final Timer quizSpreadDuration;
  private final Counter tokensInput;
  private final Counter tokensCached;
  private final Counter tokensThinking;
  private final Counter tokensOutput;
  private final Counter costEstimated;

  public GeminiMetricsRecorder(MeterRegistry registry) {
    this.chunkDuration =
        Timer.builder("gemini.chunk.duration")
            .description("Gemini API 청크별 응답 시간")
            .register(registry);
    this.firstQuizDuration =
        Timer.builder("gemini.request.first-quiz.duration")
            .description("요청 시작 → 첫 번째 퀴즈 응답까지 소요 시간")
            .register(registry);
    this.totalRequestDuration =
        Timer.builder("gemini.request.total.duration")
            .description("요청 시작 → 전체 퀴즈 생성 완료까지 소요 시간")
            .register(registry);
    this.quizSpreadDuration =
        Timer.builder("gemini.request.spread.duration")
            .description("첫 번째 퀴즈 응답 ~ 마지막 퀴즈 응답 사이 시간 차이")
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

    // 캐시 히트율 Gauge — 기존 Counter를 활용한 파생 메트릭
    // 캐시 읽기 $0.05/1M vs 일반 입력 $0.50/1M → 히트율 하락 시 비용 급증 감지용
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
   * 청크 처리 완료 후 메트릭을 기록한다. Usage에서 토큰 수를 추출하고 비용을 계산하여 Prometheus에 기록한다.
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

    long nonCachedInputTokens = promptTokens - cachedTokens;
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
   * 요청 단위 응답 시간 메트릭을 기록한다.
   *
   * @param requestStartMs 요청 시작 시각 (System.nanoTime)
   * @param firstQuizNanos 첫 번째 퀴즈 응답 시각 (System.nanoTime), null이면 퀴즈가 하나도 생성되지 않은 것
   * @param lastQuizNanos 마지막 퀴즈 응답 시각 (System.nanoTime), null이면 퀴즈가 하나도 생성되지 않은 것
   */
  public void recordRequestDuration(long requestStartMs, Long firstQuizNanos, Long lastQuizNanos) {
    long now = System.nanoTime();
    totalRequestDuration.record(now - requestStartMs, TimeUnit.NANOSECONDS);

    if (firstQuizNanos != null) {
      firstQuizDuration.record(firstQuizNanos - requestStartMs, TimeUnit.NANOSECONDS);
    }
    if (firstQuizNanos != null && lastQuizNanos != null) {
      quizSpreadDuration.record(lastQuizNanos - firstQuizNanos, TimeUnit.NANOSECONDS);
    }
  }
}
