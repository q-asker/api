package com.icc.qasker.quizset.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.util.Arrays;
import java.util.List;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 인핸스먼트 <b>쓰기(flush 더티 스캔)</b> 이득의 <b>내부(격리) 벤치</b>. end-to-end(k6+실DB)는 시스템 노이즈·드리프트에 이득이 묻혀 탐지
 * 불가였으므로(반복+대조군으로 확정), 쓰기 축은 이 인메모리 격리 벤치로만 측정한다.
 *
 * <p><b>격리 설계</b>: 세트에 N개를 managed로 로드한 뒤 <b>M개(소수, 고정)만</b> dirty로 만들고 {@code flush()}만 감싸 측정한다.
 * 로드(N)·UPDATE(M) 비용은 두 모드 공통이고, flush 더티 스캔만 다르다:
 *
 * <ul>
 *   <li>OFF(표준): flush 때 managed N개의 <b>전체 필드를 스냅샷과 deep-compare</b> → O(N×fields)
 *   <li>ON(인핸스): 세터 시점 inline dirty tracker 조회 → 전체 비교 생략
 * </ul>
 *
 * 노이즈는 warmup(JIT 예열) + min-of-iterations(GC 최소 iteration)로 제거한다.
 *
 * <p><b>ON/OFF 비교</b>(인핸스먼트는 컴파일타임 변환이라 런타임 토글 불가 — 두 빌드로 실행 후 표 비교):
 *
 * <pre>
 *   # OFF(기본)
 *   BENCHMARK=true ./gradlew :quiz-set-impl:test --tests '*SetDiversityEnhancementBenchmarkTest' --rerun-tasks
 *   # ON
 *   BENCHMARK=true ./gradlew :quiz-set-impl:test --tests '*SetDiversityEnhancementBenchmarkTest' --rerun-tasks -PenableHibernateEnhancement
 * </pre>
 *
 * N-스윕 표의 "flush min(ms)"·"per-entity(us)"를 두 빌드에서 비교하면 <b>이득이 로드 N에 비례</b>함이 드러난다(N=1이면 0, 수만에서
 * −40~50%대). 실측 확인: N=1k 무의미, N=20k에서 −44~54%.
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "BENCHMARK", matches = "true")
class SetDiversityEnhancementBenchmarkTest extends JpaIntegrationTestBase {

  private static final long SET_ID_BASE = 9_000_000L;

  private final int[] nSweep =
      envIntList("BENCH_N_SWEEP", new int[] {500, 1000, 4000, 10000, 20000});
  // M(dirty) 결정: DENOM>0이면 M=N/DENOM(현실적 배치 비율, 기본 1/3), 아니면 고정 BENCH_DIRTY(순수 격리).
  private final int dirtyDenom = envInt("BENCH_DIRTY_DENOM", 3);
  private final int dirty = envInt("BENCH_DIRTY", 5);
  private final int iterations = envInt("BENCH_ITERATIONS", 30);
  private final int warmup = envInt("BENCH_WARMUP", 8);
  private final int fillerChars = envInt("BENCH_FILLER_CHARS", 1200);

  @Autowired private ProblemQualityLogRepository repository;

  @Test
  @DisplayName("N-스윕: flush 더티스캔 비용이 로드 N에 비례 — 인핸스먼트 이득도 N에 비례")
  void flushScanCostScalesWithLoadedN() {
    boolean enhanced =
        PersistentAttributeInterceptable.class.isAssignableFrom(ProblemQualityLog.class);

    System.out.printf("%n==== SetDiversityEnhancementBenchmark (내부 격리 벤치) ====%n");
    System.out.printf(
        "인핸스먼트: %s | M(dirty)=%s, warmup=%d, iters=%d, filler=%d자%n",
        enhanced ? "ON (inline dirty tracking)" : "OFF (표준 스냅샷 비교)",
        dirtyDenom > 0 ? "N/" + dirtyDenom : String.valueOf(dirty),
        warmup,
        iterations,
        fillerChars);
    System.out.printf(
        "%-10s %-16s %-16s %-16s%n",
        "N(로드)", "flush min(ms)", "flush median(ms)", "per-entity(us)");

    for (int n : nSweep) {
      long[] flushNanos = measureFlushAtN(n);
      System.out.printf(
          "%-10d %-16.3f %-16.3f %-16.4f%n",
          n, min(flushNanos) / 1e6, median(flushNanos) / 1e6, (min(flushNanos) / 1000.0) / n);
    }
    System.out.printf("=========================================================%n%n");
  }

  /** N개를 managed로 로드 → M개만 dirty → flush()만 warmup+iters 측정. 정확성(UPDATE 수==M)도 검증. */
  private long[] measureFlushAtN(int n) {
    long setId = SET_ID_BASE + n;
    for (int number = 1; number <= n; number++) {
      em.persist(qualityRow(setId, number));
    }
    flushAndClear();

    List<ProblemQualityLog> loaded = repository.findByProblemSetIdIn(List.of(setId));
    assertThat(loaded).hasSize(n);
    int m = dirtyDenom > 0 ? Math.max(1, n / dirtyDenom) : Math.min(dirty, n);

    // 정확성: M개만 dirty → flush UPDATE 수 == M (로드 N 무관, skip-clean)
    statistics().clear();
    for (int i = 0; i < m; i++) {
      loaded.get(i).applyReview(null, "warm");
    }
    em.flush();
    assertThat(statistics().getEntityUpdateCount())
        .as("skip-clean: 로드 N=%d 중 M=%d만 UPDATE", n, m)
        .isEqualTo(m);

    long[] flushNanos = new long[iterations];
    for (int it = 0; it < warmup + iterations; it++) {
      for (int i = 0; i < m; i++) {
        loaded.get(i).applyReview(null, "v" + it); // 매 회 새 값 → 항상 dirty
      }
      long t0 = System.nanoTime();
      em.flush(); // 이 스캔이 O(N): OFF는 전체 필드 비교, ON은 tracker 조회
      long elapsed = System.nanoTime() - t0;
      if (it >= warmup) {
        flushNanos[it - warmup] = elapsed;
      }
    }
    em.clear(); // 다음 N 전에 컨텍스트 비움
    return flushNanos;
  }

  private ProblemQualityLog qualityRow(long setId, int number) {
    return ProblemQualityLog.builder()
        .problemSetId(setId)
        .number(number)
        .v1QuestionJson("{\"content\":\"" + filler(fillerChars) + "\"}")
        .v1Explanation(filler(fillerChars))
        .build();
  }

  private static String filler(int approxChars) {
    String unit = "가나다라마바사아자차 ";
    StringBuilder sb = new StringBuilder(approxChars + unit.length());
    while (sb.length() < approxChars) {
      sb.append(unit);
    }
    return sb.substring(0, approxChars);
  }

  private static long median(long[] values) {
    long[] sorted = values.clone();
    Arrays.sort(sorted);
    int mid = sorted.length / 2;
    return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
  }

  private static long min(long[] values) {
    long m = Long.MAX_VALUE;
    for (long v : values) {
      m = Math.min(m, v);
    }
    return m;
  }

  private static int envInt(String key, int def) {
    String v = System.getenv(key);
    return v == null ? def : Integer.parseInt(v.trim());
  }

  private static int[] envIntList(String key, int[] def) {
    String v = System.getenv(key);
    if (v == null || v.isBlank()) {
      return def;
    }
    return Arrays.stream(v.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
  }
}
