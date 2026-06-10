package com.icc.qasker.cost.kafka;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Outbox 구간의 분산 추적 단절을 잇는 헬퍼.
 *
 * <p>비용 이벤트는 요청 trace 안에서 Outbox로 적재되지만, 실제 Kafka 발행은 1초 뒤 별도 스케줄 스레드에서 일어나 trace가 끊긴다. 따라서 적재 시점에
 * 현재 trace 컨텍스트를 W3C {@code traceparent} 문자열로 캡처해 두었다가, 발행 시 그 컨텍스트를 복원한 scope 안에서 보내
 * producer→consumer가 원래 요청 trace를 이어받게 한다.
 *
 * <p>추적 비활성(테스트 등 Tracer 빈 부재) 시에는 모든 동작이 무해하게 no-op으로 동작한다.
 */
@Component
public class OutboxTracePropagator {

  private final Tracer tracer;
  private final Propagator propagator;

  /** Tracer/Propagator 빈이 없으면(추적 비활성) 빈 Optional이 주입되어 no-op으로 동작한다. */
  public OutboxTracePropagator(Optional<Tracer> tracer, Optional<Propagator> propagator) {
    this.tracer = tracer.orElse(null);
    this.propagator = propagator.orElse(null);
  }

  /**
   * 현재 trace 컨텍스트를 W3C {@code traceparent} 문자열로 캡처한다.
   *
   * @return traceparent 문자열, 추적 비활성이거나 활성 컨텍스트가 없으면 null
   */
  public String capture() {
    if (tracer == null || propagator == null) {
      return null;
    }
    TraceContext context = tracer.currentTraceContext().context();
    if (context == null) {
      return null;
    }
    Map<String, String> carrier = new HashMap<>();
    propagator.inject(context, carrier, Map::put);
    return carrier.get("traceparent");
  }

  /**
   * 저장된 {@code traceparent} 컨텍스트를 복원한 scope에서 action을 실행한다. 복원할 컨텍스트가 없거나 추적 비활성이면 action을 그대로
   * 실행한다.
   *
   * @param traceParent 적재 시 저장한 traceparent (null 허용)
   * @param action 컨텍스트 안에서 수행할 발행 작업
   */
  public void runInRestoredContext(String traceParent, Runnable action) {
    if (tracer == null || propagator == null || traceParent == null || traceParent.isBlank()) {
      action.run();
      return;
    }
    Span span = propagator.extract(Map.of("traceparent", traceParent), Map::get).start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      action.run();
    } finally {
      span.end();
    }
  }
}
