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

  public GenerationResultRecorder(GenerationSlackNotifier slackNotifier, MeterRegistry registry) {
    this.slackNotifier = slackNotifier;
    this.registry = registry;
    this.fulfillmentRatio =
        DistributionSummary.builder("quiz.generation.fulfillment.ratio")
            .description("요청 대비 실제 생성 문제 수 비율 (0.0~1.0)")
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

  private void incrementOutcome(String outcome, QuizType quizType) {
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", outcome)
        .tag("quiz_type", quizType.name())
        .register(registry)
        .increment();
  }
}
