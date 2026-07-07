package com.icc.qasker.quizset.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.QualityStatus;
import com.icc.qasker.quizset.entity.Rationale;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * dirty-check 스캔 벤치 — 바이트코드 인핸스먼트의 inline dirty tracking이 전체 필드 비교(스냅샷 deep-compare)를 스킵하는지 실증한다.
 * 대형 필드 (rationale JSON)를 가진 problem_quality_log 행 N개를 로드/보유한 뒤 소수만 마킹하고 flush 시간·할당을 측정한다.
 *
 * <pre>
 *   BENCHMARK=true ./gradlew :quiz-set-impl:test --tests '*DirtyCheckScanBenchmark'
 * </pre>
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "BENCHMARK", matches = "true")
class DirtyCheckScanBenchmark extends JpaIntegrationTestBase {

  private static final String FILLER = "가나다라마바사아자차 ";

  private final int[] loadedCounts = envIntList("BENCH_LOADED", new int[] {50, 200, 800});
  private final int dirty = envInt("BENCH_DIRTY", 3);
  private final int iterations = envInt("BENCH_ITERATIONS", 30);
  private final int warmup = envInt("BENCH_WARMUP", 8);
  private final int rationaleChars = envInt("BENCH_RATIONALE_CHARS", 1200);

  @Test
  @DisplayName("소수만 수정 시 flush(dirty-check) 시간·할당이 로드 수 N에 비례하지 않음")
  void flushScalesWithDirtyNotLoadedCount() {
    boolean allocOk = allocSupported();
    System.out.printf("%n==== DirtyCheckScanBenchmark (전체 필드 비교 최적화 실증) ====%n");
    System.out.printf(
        "%-8s %-14s %-16s %-16s%n", "N(로드)", "flush(ms)", "cycle 할당(KB)", "N당 할당(KB)");

    for (int n : loadedCounts) {
      long setId = seed(n);
      long[] flushNanos = new long[iterations];
      long[] cycleAlloc = new long[iterations];
      for (int it = 0; it < warmup + iterations; it++) {
        em.clear();
        long a0 = allocOk ? currentThreadAllocatedBytes() : 0L;
        List<ProblemQualityLog> all = loadAll(setId);
        for (int d = 0; d < dirty && d < all.size(); d++) {
          all.get(d).applyVerdict(QualityStatus.BELOW_THRESHOLD, "fb-" + it + "-" + d);
        }
        long t0 = System.nanoTime();
        em.flush();
        long elapsed = System.nanoTime() - t0;
        long alloc = allocOk ? currentThreadAllocatedBytes() - a0 : 0L;
        em.clear();
        if (it >= warmup) {
          flushNanos[it - warmup] = elapsed;
          cycleAlloc[it - warmup] = alloc;
        }
      }
      long medT = median(flushNanos);
      long medA = median(cycleAlloc);
      System.out.printf(
          "%-8d %-14.3f %-16.1f %-16.2f%n", n, medT / 1e6, medA / 1024.0, (medA / 1024.0) / n);
    }
    System.out.printf("=========================================================%n%n");

    long setId = seed(loadedCounts[loadedCounts.length - 1]);
    em.clear();
    List<ProblemQualityLog> all = loadAll(setId);
    for (int d = 0; d < dirty; d++) {
      all.get(d).applyVerdict(QualityStatus.BELOW_THRESHOLD, "final-" + d);
    }
    statistics().clear();
    em.flush();
    assertThat(statistics().getEntityUpdateCount()).isEqualTo(dirty);
  }

  @Test
  @DisplayName("flush만 격리 + 대규모 N — 인핸스먼트 flush-CPU 이득 실측")
  void isolatedFlushScanAtScale() {
    int[] ns = envIntList("BENCH_LOADED_LARGE", new int[] {1000, 5000, 20000});
    System.out.printf("%n==== 격리 flush 스캔 벤치 (N개 managed 유지, %d개만 dirty, 로드 제외) ====%n", dirty);
    System.out.printf(
        "%-8s %-16s %-16s %-16s%n",
        "N(managed)", "flush median(ms)", "flush min(ms)", "N당(us,min)");

    for (int n : ns) {
      List<ProblemQualityLog> managed = seedManaged(n);
      long[] flushNanos = new long[iterations];
      for (int it = 0; it < warmup + iterations; it++) {
        for (int d = 0; d < dirty && d < managed.size(); d++) {
          managed.get(d).applyVerdict(QualityStatus.BELOW_THRESHOLD, "v" + it + "-" + d);
        }
        long t0 = System.nanoTime();
        em.flush();
        long elapsed = System.nanoTime() - t0;
        if (it >= warmup) {
          flushNanos[it - warmup] = elapsed;
        }
      }
      System.out.printf(
          "%-8d %-16.3f %-16.3f %-16.3f%n",
          n, median(flushNanos) / 1e6, min(flushNanos) / 1e6, (min(flushNanos) / 1000.0) / n);
      em.clear();
    }
    System.out.printf("=========================================================%n%n");
  }

  private List<ProblemQualityLog> loadAll(long setId) {
    return em.createQuery(
            "select q from ProblemQualityLog q where q.problemSetId = :sid order by q.number",
            ProblemQualityLog.class)
        .setParameter("sid", setId)
        .getResultList();
  }

  private long seed(long n) {
    long setId = System.nanoTime();
    Rationale rationale = largeRationale();
    for (int number = 1; number <= n; number++) {
      em.persist(
          ProblemQualityLog.builder()
              .problemSetId(setId)
              .number(number)
              .qualityStatus(QualityStatus.OK)
              .rationale(rationale)
              .build());
    }
    flushAndClear();
    return setId;
  }

  private List<ProblemQualityLog> seedManaged(int n) {
    long setId = System.nanoTime();
    Rationale rationale = largeRationale();
    List<ProblemQualityLog> list = new ArrayList<>(n);
    for (int number = 1; number <= n; number++) {
      ProblemQualityLog row =
          ProblemQualityLog.builder()
              .problemSetId(setId)
              .number(number)
              .qualityStatus(QualityStatus.OK)
              .rationale(rationale)
              .build();
      em.persist(row);
      list.add(row);
    }
    em.flush();
    return list;
  }

  private Rationale largeRationale() {
    return new Rationale(
        new Rationale.SourceAnchor(1, "1장", filler(rationaleChars)),
        "학습목표",
        "UNDERSTAND",
        0.5,
        filler(rationaleChars),
        "지시 없음",
        0.8,
        new Rationale.SelfChecks(true, true, true, true),
        null,
        null);
  }

  private static String filler(int approxChars) {
    StringBuilder sb = new StringBuilder(approxChars + FILLER.length());
    while (sb.length() < approxChars) {
      sb.append(FILLER);
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

  private static boolean allocSupported() {
    return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
        && bean.isThreadAllocatedMemorySupported();
  }

  private static long currentThreadAllocatedBytes() {
    return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
        .getCurrentThreadAllocatedBytes();
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
