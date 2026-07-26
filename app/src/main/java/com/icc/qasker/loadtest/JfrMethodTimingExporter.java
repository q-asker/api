package com.icc.qasker.loadtest;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.consumer.RecordingStream;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * JEP 520 {@code jdk.MethodTiming} 이벤트를 실행 중에 구독해(JFR 이벤트 스트리밍) Micrometer 게이지로 노출한다 — run.sh가 .jfr
 * 파일을 사후 파싱하던 것을 Prometheus 스크레이프 경로에 합류시켜, 대시보드(qasker-enh-rw)가 mode=off|on 태그로 메서드 수준 계측을
 * 지연·CPU·할당 패널과 나란히 보여줄 수 있게 한다.
 *
 * <ul>
 *   <li>{@code jfr_method_invocations{method=...}} — 앱 기동 이후 누적 호출 수
 *   <li>{@code jfr_method_time_total_seconds{method=...}} — 누적 실행 시간(= 호출 수 × 평균, 경과 시간)
 * </ul>
 *
 * <p>이벤트는 주기 발행되는 누적 스냅샷이라 게이지로 두고, 대시보드는 다른 패널과 동일하게 {@code last_over_time(...[$__range])}로 런 종료
 * 시점 값을 집는다. 오버로드가 여러 개인 메서드(areEquals)는 시그니처별 이벤트를 내부 맵에 따로 쌓고 같은 {@code Class.method} 라벨로 합산한다.
 *
 * <p>계측 대상 필터는 run.sh의 MT_FILTER 6종과 동기 유지할 것. -XX:StartFlightRecording과 동시 사용 가능 (JFR 다중 레코딩 허용 —
 * 계측은 한 번만 심어진다).
 */
@Component
@Profile("local")
public class JfrMethodTimingExporter {

  /**
   * run.sh MT_FILTER와 동일: 순수 CPU 5종(더티체크 4 + 조립 창 readRow) + 혼합 창 1종(executeQuery — 대기 포함 wall이라
   * CPU 주장 금지, 지연 분해용).
   */
  private static final String FILTER =
      "org.hibernate.event.internal.DefaultFlushEntityEventListener::getDirtyPropertiesFromSelfDirtinessTracker;"
          + "org.hibernate.persister.entity.AbstractEntityPersister::resolveDirtyAttributeIndexes;"
          + "org.hibernate.bytecode.enhance.internal.bytebuddy.InlineDirtyCheckerEqualsHelper::areEquals;"
          + "org.hibernate.event.internal.DefaultFlushEntityEventListener::performDirtyCheck;"
          + "org.hibernate.sql.results.internal.StandardRowReader::readRow;"
          + "org.hibernate.sql.results.jdbc.internal.DeferredResultSetAccess::executeQuery";

  private record Stats(long invocations, double totalSeconds) {}

  private final MeterRegistry registry;

  /** 시그니처 전체(오버로드 구분) → 최신 누적 스냅샷. */
  private final Map<String, Stats> bySignature = new ConcurrentHashMap<>();

  /** 게이지를 등록한 짧은 라벨(Class.method) 집합 — 중복 등록 방지. */
  private final Map<String, Boolean> registered = new ConcurrentHashMap<>();

  private RecordingStream stream;

  public JfrMethodTimingExporter(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * FILTER 5종의 짧은 라벨 — 기동 시 0으로 선등록해 시리즈가 앱 시작부터 존재하게 한다. 지연 등록이면 첫 이벤트 값(예: 장부 읽기 100)으로 시리즈가 태어나
   * increase()가 도약을 놓치고, pooled(범위 평균) 패널에서 "1회차 요청에 활동이 몰린" 지표가 0으로 보인다.
   */
  private static final String[] KNOWN_LABELS = {
    "DefaultFlushEntityEventListener.getDirtyPropertiesFromSelfDirtinessTracker",
    "AbstractEntityPersister.resolveDirtyAttributeIndexes",
    "InlineDirtyCheckerEqualsHelper.areEquals",
    "DefaultFlushEntityEventListener.performDirtyCheck",
    "StandardRowReader.readRow",
    "DeferredResultSetAccess.executeQuery",
  };

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    for (String label : KNOWN_LABELS) {
      registerGauges(label);
    }
    stream = new RecordingStream();
    stream.enable("jdk.MethodTiming").with("filter", FILTER).withPeriod(Duration.ofSeconds(5));
    stream.onEvent(
        "jdk.MethodTiming",
        event -> {
          var method = event.getValue("method");
          if (!(method instanceof jdk.jfr.consumer.RecordedMethod recorded)) {
            return;
          }
          String fqcn = recorded.getType().getName();
          String shortLabel = fqcn.substring(fqcn.lastIndexOf('.') + 1) + "." + recorded.getName();
          String signature = fqcn + "." + recorded.getName() + recorded.getDescriptor();

          long invocations = event.getLong("invocations");
          // average는 호출이 없으면 결측 — 그 경우 시간 0으로 둔다
          double totalSeconds = 0;
          if (invocations > 0) {
            Duration avg = event.getDuration("average");
            totalSeconds = invocations * avg.toNanos() / 1e9;
          }
          bySignature.put(signature, new Stats(invocations, totalSeconds));
          registerGauges(shortLabel); // MT_EXTRA_FILTER로 추가된 메서드는 여기서 지연 등록
        });
    stream.startAsync();
  }

  private void registerGauges(String label) {
    registered.computeIfAbsent(
        label,
        l -> {
          Gauge.builder("jfr.method.invocations", () -> sum(l, Stats::invocations))
              .tag("method", l)
              .description("JEP 520 메서드 계측 — 앱 기동 이후 누적 호출 수")
              .register(registry);
          Gauge.builder("jfr.method.time.total", () -> sum(l, Stats::totalSeconds))
              .tag("method", l)
              .baseUnit("seconds")
              .description("JEP 520 메서드 계측 — 누적 실행 시간(경과 시간)")
              .register(registry);
          return Boolean.TRUE;
        });
  }

  /** 같은 Class.method 라벨에 속한 오버로드들의 값 합산. */
  private double sum(String shortLabel, java.util.function.ToDoubleFunction<Stats> field) {
    double total = 0;
    for (Map.Entry<String, Stats> entry : bySignature.entrySet()) {
      String signature = entry.getKey();
      String beforeDescriptor = signature.substring(0, signature.indexOf('('));
      if (beforeDescriptor.endsWith(shortLabel)) {
        total += field.applyAsDouble(entry.getValue());
      }
    }
    return total;
  }

  @PreDestroy
  public void stop() {
    if (stream != null) {
      stream.close();
    }
  }
}
