package com.icc.qasker.quiz.service.generation;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 퀴즈 생성 결과에 대한 Slack 알림 + Prometheus 메트릭을 통합 기록한다. */
@Component
public class GenerationResultRecorder {

  private final GenerationSlackNotifier slackNotifier;
  private final MeterRegistry registry;

  public GenerationResultRecorder(GenerationSlackNotifier slackNotifier, MeterRegistry registry) {
    this.slackNotifier = slackNotifier;
    this.registry = registry;

    // 모든 QuizType × outcome/metric 조합을 미리 등록
    // (quizzes.requested/generated는 max_chunks 태그가 동적으로 부여되어 eager 등록 생략)
    for (QuizType qt : QuizType.values()) {
      String type = qt.name();
      for (String outcome : new String[] {"success", "partial", "fail"}) {
        Counter.builder("quiz.generation.outcome")
            .tag("outcome", outcome)
            .tag("quiz_type", type)
            .register(registry);
      }
      for (int quizCount : new int[] {5, 10, 15, 20, 25}) {
        Counter.builder("quiz.generation.requests")
            .description("퀴즈 생성 요청 횟수 (타입+문제수별)")
            .tag("quiz_type", type)
            .tag("quiz_count", String.valueOf(quizCount))
            .register(registry);
      }
    }
  }

  public void recordSuccess(Long problemSetId, QuizType quizType, long generatedCount) {
    slackNotifier.notifySuccess(problemSetId, quizType, generatedCount);
    incrementOutcome("success", quizType);
  }

  public void recordPartialSuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    slackNotifier.notifyPartialSuccess(problemSetId, quizType, generatedCount, quizCount);
    incrementOutcome("partial", quizType);
  }

  public void recordError(Long problemSetId, String errorMessage) {
    slackNotifier.notifyError(problemSetId, errorMessage);
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", "fail")
        .register(registry)
        .increment();
  }

  /** 요청/생성 문제 수를 퀴즈 타입별로 기록한다. finalize 결과와 무관하게 호출된다. */
  public void recordQuizCounts(
      QuizType quizType, long quizCount, long generatedCount, int maxChunkCount) {
    String type = quizType.name();
    String count = String.valueOf(quizCount);
    String chunks = String.valueOf(maxChunkCount);

    // 퀴즈 타입 + 문제 수별 요청 횟수 (예: MULTIPLE/10문제 → 20회)
    Counter.builder("quiz.generation.requests")
        .description("퀴즈 생성 요청 횟수 (타입+문제수별)")
        .tag("quiz_type", type)
        .tag("quiz_count", count)
        .register(registry)
        .increment();

    Counter.builder("quiz.generation.quizzes.requested")
        .description("요청된 퀴즈 문제 수 누적")
        .tag("quiz_type", type)
        .tag("max_chunks", chunks)
        .register(registry)
        .increment(quizCount);
    Counter.builder("quiz.generation.quizzes.generated")
        .description("실제 생성된 퀴즈 문제 수 누적")
        .tag("quiz_type", type)
        .tag("max_chunks", chunks)
        .register(registry)
        .increment(generatedCount);
  }

  private void incrementOutcome(String outcome, QuizType quizType) {
    Counter.builder("quiz.generation.outcome")
        .tag("outcome", outcome)
        .tag("quiz_type", quizType.name())
        .register(registry)
        .increment();
  }
}
