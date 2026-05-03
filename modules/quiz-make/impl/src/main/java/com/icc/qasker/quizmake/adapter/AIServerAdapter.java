package com.icc.qasker.quizmake.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Gemini API 호출을 서킷브레이커로 보호하는 어댑터.
 *
 * <p>서킷브레이커 측정 단위는 <b>첫 퀴즈 응답(TTFQ)까지의 시간</b>이다. {@code generateQuiz()}는 끝까지 실행되지만, 서킷브레이커에는 첫 응답이
 * 도달한 시점까지의 시간만 보고된다. 이는 사용자 체감 지연(첫 퀴즈가 화면에 뜨기까지)을 정확히 보호하기 위함이다.
 *
 * <p>인프라 장애(파일 업로드, 캐시 생성/삭제 실패)는 GeminiInfraException으로 전파되어 서킷브레이커 카운트 대상이 된다. 다만 첫 응답이 정상 도달한 후
 * 발생한 인프라 장애는 카운트하지 않는다 (이미 사용자에게 첫 응답이 전달되었으므로 인프라는 정상으로 간주).
 *
 * <p>참고: 사용자 체감 TTFQ는 별도 메트릭 {@code gemini_request_first_quiz_duration_seconds}로도 측정된다 (p50 13.6s, p95
 * 27.4s, p99 ≥30s actuator 버킷 절단, max 119s — 2026-05-03 실측).
 */
@Component
public class AIServerAdapter {

  private static final String CIRCUIT_BREAKER_NAME = "aiServer";

  private final QuizOrchestrationService quizOrchestrationService;
  private final CircuitBreaker circuitBreaker;

  public AIServerAdapter(
      QuizOrchestrationService quizOrchestrationService,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.quizOrchestrationService = quizOrchestrationService;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
  }

  /**
   * Gemini API를 통해 퀴즈를 생성한다. 서킷브레이커는 첫 응답까지의 시간(TTFQ)만 측정 단위로 사용한다.
   *
   * @return 이 요청에 적용된 최대 청크 수 (max_chunks)
   */
  public int streamRequest(GenerationRequestToAI request) {
    if (!circuitBreaker.tryAcquirePermission()) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR,
          "AI Server Unavailable (Circuit Open)",
          CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
    }

    long startNanos = System.nanoTime();
    AtomicLong firstResponseNanos = new AtomicLong(0);

    GenerationRequestToAI wrappedRequest =
        wrapWithFirstResponseTracker(request, firstResponseNanos);

    try {
      int maxChunkCount = quizOrchestrationService.generateQuiz(wrappedRequest);
      reportToCircuitBreaker(startNanos, firstResponseNanos.get(), null);
      return maxChunkCount;
    } catch (GeminiInfraException e) {
      reportToCircuitBreaker(startNanos, firstResponseNanos.get(), e);
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
          e);
    } catch (Exception e) {
      reportToCircuitBreaker(startNanos, firstResponseNanos.get(), e);
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI Server Unknown Error", e);
    }
  }

  /** questionsConsumer를 래핑하여 첫 응답 시각을 기록한다. */
  private GenerationRequestToAI wrapWithFirstResponseTracker(
      GenerationRequestToAI request, AtomicLong firstResponseNanos) {
    Consumer<AIProblemSet> originalConsumer = request.questionsConsumer();
    Consumer<AIProblemSet> wrappedConsumer =
        problemSet -> {
          firstResponseNanos.compareAndSet(0, System.nanoTime());
          originalConsumer.accept(problemSet);
        };

    return GenerationRequestToAI.builder()
        .fileUrl(request.fileUrl())
        .strategyValue(request.strategyValue())
        .language(request.language())
        .quizCount(request.quizCount())
        .referencePages(request.referencePages())
        .questionsConsumer(wrappedConsumer)
        .customInstruction(request.customInstruction())
        .build();
  }

  /**
   * 서킷브레이커에 첫 응답까지의 시간을 보고한다.
   *
   * <p>정책:
   *
   * <ul>
   *   <li><b>정상 종료</b>: 첫 응답까지의 시간을 onSuccess로 보고 (slow-call 판정 대상)
   *   <li><b>첫 응답 후 예외</b>: 사용자에게 첫 응답이 이미 전달됐으므로 인프라는 정상으로 간주 → onSuccess. 의도된 트레이드오프: 후속 청크 단계
   *       인프라 장애는 서킷 미카운트
   *   <li><b>첫 응답 전 GeminiInfraException</b>: 인프라 장애 → onError로 실패 카운트
   *   <li><b>첫 응답 전 그 외 예외</b>: yml의 record-exceptions 정책(GeminiInfraException만)을 코드 레벨에서 일치시키기 위해
   *       releasePermission으로 카운트 제외 (programmatic onError는 record-exceptions 필터를 우회하므로 명시적 분기가 필요)
   * </ul>
   */
  private void reportToCircuitBreaker(long startNanos, long firstResponseNanos, Throwable failure) {
    long durationNanos =
        firstResponseNanos > 0 ? firstResponseNanos - startNanos : System.nanoTime() - startNanos;

    if (failure == null || firstResponseNanos > 0) {
      circuitBreaker.onSuccess(durationNanos, TimeUnit.NANOSECONDS);
      return;
    }

    if (failure instanceof GeminiInfraException) {
      circuitBreaker.onError(durationNanos, TimeUnit.NANOSECONDS, failure);
      return;
    }

    circuitBreaker.releasePermission();
  }
}
