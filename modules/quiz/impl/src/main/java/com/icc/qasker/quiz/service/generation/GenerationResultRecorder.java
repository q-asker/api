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

    // [Prometheus] quiz_generation_fulfillment_ratio — DistributionSummary
    // 용도: 요청한 문제 수 대비 실제 생성된 문제 수의 비율(0.0~1.0)을 분포로 추적한다.
    //       프롬프트 변경 전후의 fulfillment 비율을 비교하여 개선 효과를 정량 검증할 수 있다.
    // 예시: 10문제 요청 → 7문제 생성 시 0.7 기록, 10문제 요청 → 10문제 생성 시 1.0 기록
    // Prometheus 노출: quiz_generation_fulfillment_ratio_sum (비율 합계)
    //                  quiz_generation_fulfillment_ratio_count (기록 횟수)
    //                  → 평균 = sum / count
    this.fulfillmentRatio =
        DistributionSummary.builder("quiz.generation.fulfillment.ratio")
            .description("요청 대비 실제 생성 문제 수 비율 (0.0~1.0)")
            .register(registry);
  }

  public void recordSuccess(Long problemSetId, QuizType quizType, long generatedCount) {
    slackNotifier.notifySuccess(problemSetId, quizType, generatedCount);

    // [Prometheus] quiz_generation_outcome_total{outcome="success", quiz_type="MULTIPLE"} — Counter
    // 용도: 퀴즈 생성이 요청한 문제 수를 100% 충족한 횟수를 센다.
    // Grafana: Pie Chart로 success/partial/fail 비율 시각화
    incrementOutcome("success", quizType);

    // [Prometheus] quiz_generation_fulfillment_ratio — 1.0 기록 (100% 충족)
    fulfillmentRatio.record(1.0);
  }

  public void recordPartialSuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    slackNotifier.notifyPartialSuccess(problemSetId, quizType, generatedCount, quizCount);

    // [Prometheus] quiz_generation_outcome_total{outcome="partial", quiz_type="OX"} — Counter
    // 용도: 퀴즈 생성이 일부만 성공한 횟수를 센다. (예: 10문제 요청 → 7문제만 생성)
    incrementOutcome("partial", quizType);

    // [Prometheus] quiz_generation_fulfillment_ratio — 실제 비율 기록
    // 예시: generatedCount=7, quizCount=10 → 0.7 기록
    //       이 값의 분포를 추적하면 "partial일 때 평균 몇 %나 생성되는가?"를 알 수 있다.
    fulfillmentRatio.record((double) generatedCount / quizCount);
  }

  public void recordError(Long problemSetId, String errorMessage) {
    slackNotifier.notifyError(problemSetId, errorMessage);

    // [Prometheus] quiz_generation_outcome_total{outcome="fail"} — Counter
    // 용도: 퀴즈 생성이 완전히 실패한(0문제 생성) 횟수를 센다.
    // 참고: 에러 시에는 QuizType을 알 수 없으므로 quiz_type 태그를 생략한다.
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", "fail")
        .register(registry)
        .increment();

    // [Prometheus] quiz_generation_fulfillment_ratio — 0.0 기록 (0% 충족)
    fulfillmentRatio.record(0.0);
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
