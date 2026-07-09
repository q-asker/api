package com.icc.qasker.ai.service.support;

import com.icc.qasker.ai.properties.QAskerAiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini API 호출에 대한 Prometheus 메트릭을 통합 기록한다. 비즈니스 로직(QuizOrchestrationServiceImpl)에서 메트릭 관심사를 분리하기
 * 위한 Delegation 패턴. 모든 메트릭은 eager 등록 — Prometheus가 첫 스크랩에서 0을 관측하여 increase()가 첫 요청부터 정확히 동작한다.
 */
@Slf4j
@Component
public class GeminiMetricsRecorder {

  private final double priceInputPer1M;
  private final double priceCacheReadPer1M;
  private final double priceOutputPer1M;

  private final MeterRegistry registry;
  private final Timer chunkDuration;
  private final Counter tokensInput;
  private final Counter tokensCached;
  private final Counter tokensThinking;
  private final Counter tokensOutput;
  private final Timer gradingDuration;
  private final Counter gradingTokensInput;
  private final Counter gradingTokensOutput;
  private final Counter gradingCost;
  private final Counter gradingCount;
  private final Counter gradingFailure;
  private final Timer verifyDuration;
  private final Counter verifyTokensInput;
  private final Counter verifyTokensOutput;
  private final Counter verifyCost;
  private final Counter verifyCount;
  private final Counter verifyFailure;

  private static final String[] QUIZ_TYPES = {"MULTIPLE", "OX", "BLANK", "ESSAY"};

  public GeminiMetricsRecorder(
      MeterRegistry registry,
      QAskerAiProperties aiProperties,
      @Value("${q-asker.ai.generation.price-input-per-1m}") double priceInputPer1M,
      @Value("${q-asker.ai.generation.price-cache-read-per-1m}") double priceCacheReadPer1M,
      @Value("${q-asker.ai.generation.price-output-per-1m}") double priceOutputPer1M) {
    this.registry = registry;
    this.priceInputPer1M = priceInputPer1M;
    this.priceCacheReadPer1M = priceCacheReadPer1M;
    this.priceOutputPer1M = priceOutputPer1M;
    this.gradingDuration =
        Timer.builder("gemini.grading.duration")
            .description("ESSAY 채점 API 응답 시간")
            .register(registry);
    this.gradingTokensInput =
        Counter.builder("gemini.grading.tokens.input")
            .description("ESSAY 채점 API 입력 토큰")
            .register(registry);
    this.gradingTokensOutput =
        Counter.builder("gemini.grading.tokens.output")
            .description("ESSAY 채점 API 출력 토큰")
            .register(registry);
    this.gradingCost =
        Counter.builder("gemini.grading.cost")
            .description("ESSAY 채점 API 추정 비용 (USD)")
            .register(registry);
    this.gradingCount =
        Counter.builder("gemini.grading.count").description("ESSAY 채점 요청 횟수").register(registry);
    this.gradingFailure =
        Counter.builder("gemini.grading.failure").description("ESSAY 채점 실패 횟수").register(registry);

    this.verifyDuration =
        Timer.builder("gemini.verify.duration")
            .description("문항 품질 검증 API 응답 시간")
            .register(registry);
    this.verifyTokensInput =
        Counter.builder("gemini.verify.tokens.input")
            .description("문항 품질 검증 API 입력 토큰")
            .register(registry);
    this.verifyTokensOutput =
        Counter.builder("gemini.verify.tokens.output")
            .description("문항 품질 검증 API 출력 토큰")
            .register(registry);
    this.verifyCost =
        Counter.builder("gemini.verify.cost")
            .description("문항 품질 검증 API 추정 비용 (USD)")
            .register(registry);
    this.verifyCount =
        Counter.builder("gemini.verify.count").description("문항 품질 검증 요청 횟수").register(registry);
    this.verifyFailure =
        Counter.builder("gemini.verify.failure").description("문항 품질 검증 실패 횟수").register(registry);

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

    // 스트리밍 타임아웃 카운터를 퀴즈 타입별로 미리 등록
    for (String quizType : QUIZ_TYPES) {
      Counter.builder("gemini.streaming.timeout")
          .description("Gemini 스트리밍 5분 타임아웃 발생 횟수")
          .tag("quiz_type", quizType)
          .register(registry);
    }

    // 요청 단위 메트릭을 max_chunks 변형별로 미리 등록
    for (int variant : aiProperties.getChunk().getMaxCountVariants()) {
      String tag = String.valueOf(variant);
      Timer.builder("gemini.request.total.duration")
          .description("요청 시작 → 전체 퀴즈 생성 완료까지 소요 시간")
          .tag("max_chunks", tag)
          .register(registry);
      Timer.builder("gemini.request.first-quiz.duration")
          .description("요청 시작 → 첫 번째 퀴즈 응답까지 소요 시간")
          .tag("max_chunks", tag)
          .register(registry);
      Timer.builder("gemini.request.spread.duration")
          .description("첫 번째 퀴즈 응답 ~ 마지막 퀴즈 응답 사이 시간 차이")
          .tag("max_chunks", tag)
          .register(registry);
      Counter.builder("gemini.request.cost")
          .description("요청 단위 추정 비용 합계 (USD)")
          .tag("max_chunks", tag)
          .register(registry);
    }
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
    double inputCost = nonCachedInputTokens * priceInputPer1M / 1_000_000;
    double cacheCost = cachedTokens * priceCacheReadPer1M / 1_000_000;
    double outputCost = completionTokens * priceOutputPer1M / 1_000_000;
    double thinkingCost = thoughtsTokens * priceOutputPer1M / 1_000_000;
    double totalCost = inputCost + cacheCost + outputCost + thinkingCost;

    chunkDuration.record(elapsedMs, TimeUnit.MILLISECONDS);
    tokensInput.increment(nonCachedInputTokens);
    tokensCached.increment(cachedTokens);
    tokensThinking.increment(thoughtsTokens);
    tokensOutput.increment(completionTokens);

    return totalCost;
  }

  /**
   * 청크 usage를 기록하고 토큰/비용을 로깅한 뒤 추정 비용을 반환한다. 오케스트레이터의 doOnNext usage 로깅 중복을 흡수한다.
   *
   * @param label 로그 식별 라벨 (예: "MULTIPLE chunk #0", "streaming")
   * @param startNanos 요청 시작 시각 (System.nanoTime)
   * @param usage ChatResponse에서 추출한 Usage 메타데이터
   * @return 추정 비용 (USD)
   */
  public double recordChunkUsage(String label, long startNanos, Usage usage) {
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
    double cost = recordChunkResult(elapsedMs, usage);
    long thinkingTokens =
        usage instanceof GoogleGenAiUsage g && g.getThoughtsTokenCount() != null
            ? g.getThoughtsTokenCount()
            : 0;
    log.info(
        "Gemini Usage - {}, 토큰: 입력={}, 추론={}, 출력={}, 비용=${}",
        label,
        usage.getPromptTokens(),
        thinkingTokens,
        usage.getCompletionTokens(),
        String.format("%.6f", cost));
    return cost;
  }

  /** ESSAY 채점 완료 메트릭을 기록한다. */
  public void recordGrading(long elapsedMs, long inputTokens, long outputTokens, double cost) {
    gradingDuration.record(elapsedMs, TimeUnit.MILLISECONDS);
    gradingTokensInput.increment(inputTokens);
    gradingTokensOutput.increment(outputTokens);
    gradingCost.increment(cost);
    gradingCount.increment();
  }

  /** ESSAY 채점 실패를 기록한다. */
  public void recordGradingFailure() {
    gradingFailure.increment();
  }

  /** 문항 품질 검증 완료 메트릭을 기록한다. */
  public void recordVerify(long elapsedMs, long inputTokens, long outputTokens, double cost) {
    verifyDuration.record(elapsedMs, TimeUnit.MILLISECONDS);
    verifyTokensInput.increment(inputTokens);
    verifyTokensOutput.increment(outputTokens);
    verifyCost.increment(cost);
    verifyCount.increment();
  }

  /** 문항 품질 검증 실패(검증 불가)를 기록한다. */
  public void recordVerifyFailure() {
    verifyFailure.increment();
  }

  /** 스트리밍 타임아웃 발생을 기록한다. */
  public void recordStreamingTimeout(String quizType) {
    Counter.builder("gemini.streaming.timeout")
        .description("Gemini 스트리밍 5분 타임아웃 발생 횟수")
        .tag("quiz_type", quizType)
        .register(registry)
        .increment();
  }

  /**
   * 요청 단위 응답 시간 메트릭을 기록한다. A/B 테스트용 max_chunks 태그를 동적으로 부여한다.
   *
   * @param maxChunkCount 이 요청에 적용된 최대 청크 수
   * @param requestStartNanos 요청 시작 시각 (System.nanoTime)
   * @param firstQuizNanos 첫 번째 퀴즈 응답 시각, null이면 퀴즈 미생성
   * @param lastQuizNanos 마지막 퀴즈 응답 시각, null이면 퀴즈 미생성
   * @param totalCost 이 요청의 청크별 비용 합계 (USD)
   */
  public void recordRequestDuration(
      int maxChunkCount,
      long requestStartNanos,
      Long firstQuizNanos,
      Long lastQuizNanos,
      double totalCost) {
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

    // 요청 단위 비용 합계 (max_chunks 태그 포함)
    Counter.builder("gemini.request.cost")
        .description("요청 단위 추정 비용 합계 (USD)")
        .tag("max_chunks", tag)
        .register(registry)
        .increment(totalCost);
  }
}
