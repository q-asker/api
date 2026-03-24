package com.icc.qasker.quiz.service.generation;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 퀴즈 생성 결과에 대한 Slack 알림 + Prometheus 메트릭을 통합 기록한다. */
@Component
public class GenerationResultRecorder {

  private final GenerationSlackNotifier slackNotifier;
  private final MeterRegistry registry;
  private final DistributionSummary fulfillmentRatio;
  private final Counter quizzesRequested;
  private final Counter quizzesGenerated;
  private final Counter quizzesFailed;

  public GenerationResultRecorder(GenerationSlackNotifier slackNotifier, MeterRegistry registry) {
    this.slackNotifier = slackNotifier;
    this.registry = registry;

    this.fulfillmentRatio =
        DistributionSummary.builder("quiz.generation.fulfillment.ratio")
            .description("요청 대비 실제 생성 문제 수 비율 (0.0~1.0)")
            .register(registry);
    this.quizzesRequested =
        Counter.builder("quiz.generation.quizzes.requested")
            .description("요청된 퀴즈 문제 수 누적")
            .register(registry);
    this.quizzesGenerated =
        Counter.builder("quiz.generation.quizzes.generated")
            .description("실제 생성된 퀴즈 문제 수 누적")
            .register(registry);
    this.quizzesFailed =
        Counter.builder("quiz.generation.quizzes.failed")
            .description("생성 실패한 퀴즈 문제 수 누적")
            .register(registry);
  }

  public void recordSuccess(Long problemSetId, QuizType quizType, long generatedCount) {
    slackNotifier.notifySuccess(problemSetId, quizType, generatedCount);
    incrementOutcome("success", quizType);
    fulfillmentRatio.record(1.0);
  }

  public void recordPartialSuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    slackNotifier.notifyPartialSuccess(problemSetId, quizType, generatedCount, quizCount);
    incrementOutcome("partial", quizType);
    fulfillmentRatio.record((double) generatedCount / quizCount);
  }

  public void recordError(Long problemSetId, String errorMessage) {
    slackNotifier.notifyError(problemSetId, errorMessage);
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", "fail")
        .register(registry)
        .increment();
    fulfillmentRatio.record(0.0);
  }

  /** 요청/생성/실패 문제 수를 기록한다. finalize 결과와 무관하게 호출된다. */
  public void recordQuizCounts(long quizCount, long generatedCount) {
    quizzesRequested.increment(quizCount);
    quizzesGenerated.increment(generatedCount);
    long failed = quizCount - generatedCount;
    if (failed > 0) {
      quizzesFailed.increment(failed);
    }
  }

  /**
   * [Prometheus] quiz_generation_outcome_total{outcome=..., quiz_type=...} — Counter 용도: 퀴즈 생성 결과를
   * outcome(success/partial/fail)과 quiz_type(MULTIPLE/OX/BLANK)별로 카운트한다. 동적 태그: Micrometer는 동일
   * 이름+태그 조합을 내부 캐싱하므로 매 호출마다 builder().register()해도 안전하다. Grafana PromQL 예시: - 전체 분포:
   * quiz_generation_outcome_total (Pie Chart) - 분당 추이: rate(quiz_generation_outcome_total[5m])
   * (Stacked Time Series) - 실패율: rate(quiz_generation_outcome_total{outcome="fail"}[5m])
   */
  private void incrementOutcome(String outcome, QuizType quizType) {
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", outcome)
        .tag("quiz_type", quizType.name())
        .register(registry)
        .increment();
  }
}
