package com.icc.qasker.quizset.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.service.quality.ExplanationFormatValidator;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import com.icc.qasker.quizset.support.SqlCapture;
import jakarta.persistence.PersistenceUnitUtil;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * explanation-review 리소스 벤치 — 일반 모드 vs Hibernate 인핸스먼트 모드에서 {@code POST
 * /admin/problem-sets/{setId}/explanation-review}의 <b>읽기 경로</b> 리소스 사용량을 비교한다.
 *
 * <p>이 엔드포인트는 로그 행의 해설(v2 우선, 없으면 v1)만 읽어 형식 검증하고 미달분에만 review를 마킹한다. 최대 blob인 질문 JSON(v1/v2)은 읽지
 * 않는다. {@code @Basic(fetch=LAZY) @LazyGroup("question")}이 걸린 질문 JSON은 인핸스먼트 계측이 켜진 빌드에서만 실제로 지연
 * 로딩되어(계측 OFF 시 즉시 로딩 폴백) 로드 경로의 전송량이 줄어든다. 이 벤치는 CPU-time({@link
 * ThreadMXBean#getCurrentThreadCpuTime()})과 힙 사용량({@code MemoryMXBean} — 프로메테우스 {@code
 * jvm_memory_used_bytes{area="heap"}}와 동일 소스)으로 측정한다. 읽기 벤치(perf-seed의 {@code measure.sh})가 앱
 * CPU-time·힙을 수집하는 방식을 인프로세스 격리 벤치로 옮긴 것이다.
 *
 * <p>쓰기/flush 경로(부분 컬럼 UPDATE·skip-clean)는 이 최적화와 무관하며 {@link DirtyCheckScanBenchmark}가 별도로 다룬다.
 * 여기서는 시드를 정형 해설로 채워 마킹(쓰기)을 배제하고 순수 로드+검증 비용만 격리한다.
 *
 * <p><b>H2 한계</b>: 인메모리 H2는 TEXT를 프로세스 내 자바 String으로 보유하고 {@code getString()}이 참조를 거의 그대로 넘겨(직렬화·소켓
 * 전송·복사 없음) 컬럼 크기와 무관하게 CPU-time·힙이 평평하다. 따라서 이 벤치의 CPU-time/힙 열은 지연 로딩의 절감폭을 보여주지 못한다(프로덕션 MySQL은
 * 소켓 전송·역직렬화 비용이 실재하며 지연 로딩이 이를 제거한다). 이 하네스가 <b>엄밀히 증명하는 것은 메커니즘</b>이다: 인핸스먼트 모드에서 SELECT가 질문 JSON
 * 컬럼을 제외하고(전송 대상 축소) 읽기 경로에 N+1이 없음. 절감 <i>규모</i>는 MySQL 실측이 필요하다.
 *
 * <pre>
 *   일반 모드:      BENCHMARK=true ./gradlew :quiz-set-impl:test --tests '*ExplanationReviewResourceBenchmark' --rerun-tasks
 *   인핸스먼트 모드: BENCHMARK=true ./gradlew clean :quiz-set-impl:test --tests '*ExplanationReviewResourceBenchmark' -PenableHibernateEnhancement --rerun-tasks
 * </pre>
 *
 * (인핸스먼트 빌드는 계측 클래스 캐시 혼입 방지를 위해 반드시 {@code clean} 후 재빌드한다.)
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "BENCHMARK", matches = "true")
@TestPropertySource(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.icc.qasker.quizset.support.SqlCapture")
class ExplanationReviewResourceBenchmark extends JpaIntegrationTestBase {

  private static final String FILLER = "가나다라마바사아자차 ";

  @Autowired private ProblemQualityLogRepository repository;

  private final ExplanationFormatValidator validator = new ExplanationFormatValidator();

  private final int[] loadedCounts = envIntList("BENCH_LOADED", new int[] {1000});
  private final int iterations = envInt("BENCH_ITERATIONS", 30);
  private final int warmup = envInt("BENCH_WARMUP", 8);
  private final int questionChars = envInt("BENCH_QUESTION_CHARS", 4000);
  private final int explanationChars = envInt("BENCH_EXPLANATION_CHARS", 600);

  @Test
  @DisplayName("explanation-review 읽기 경로 — 질문 JSON 지연 로딩 실증(SELECT 컬럼 제외·N+1 없음)")
  void reviewReadPathResourceUsage() {
    boolean enhanced =
        PersistentAttributeInterceptable.class.isAssignableFrom(ProblemQualityLog.class);
    boolean cpuOk = cpuSupported();

    System.out.printf("%n==== ExplanationReviewResourceBenchmark ====%n");
    System.out.printf(
        "모드: %s — 질문 JSON(v1/v2) %s%n",
        enhanced ? "인핸스먼트(계측 ON)" : "일반(계측 OFF)",
        enhanced ? "지연 로딩(읽기 경로에서 미조회)" : "즉시 로딩(폴백, 전량 조회)");
    verifyLazyState(enhanced);
    System.out.printf(
        "%-8s %-16s %-16s %-16s %-10s%n",
        "N(로드)", "CPU-time/N(µs)", "CPU-time합(ms)", "힙 증가(MB)", "queries");

    for (int n : loadedCounts) {
      long setId = seed(n);
      long[] cpuNanos = new long[iterations];
      long[] heapDelta = new long[iterations];
      long queries = 0;
      for (int it = 0; it < warmup + iterations; it++) {
        em.clear();
        statistics().clear();
        long h0 = heapUsedBytes();
        long c0 = cpuOk ? currentThreadCpuTime() : 0L;
        int violations = reviewReadPath(setId);
        long cpu = cpuOk ? currentThreadCpuTime() - c0 : 0L;
        long heap = Math.max(0L, heapUsedBytes() - h0);
        // 정형 해설로 시드 → 형식 통과 → 마킹(쓰기) 없음. 로드+검증만 격리.
        assertThat(violations).isZero();
        if (it >= warmup) {
          cpuNanos[it - warmup] = cpu;
          heapDelta[it - warmup] = heap;
          queries = statistics().getQueryExecutionCount();
        }
      }
      long medCpu = median(cpuNanos);
      long medHeap = median(heapDelta);
      System.out.printf(
          "%-8d %-16.2f %-16.3f %-16.2f %-10d%n",
          n, (medCpu / 1000.0) / n, medCpu / 1e6, medHeap / (1024.0 * 1024.0), queries);
    }
    System.out.printf("=========================================================%n%n");
  }

  /** 서비스의 읽기+검증 흐름을 그대로 재현한다(마킹 제외 — 쓰기 경로는 별도 벤치). */
  private int reviewReadPath(long setId) {
    List<ProblemQualityLog> logs = repository.findByProblemSetIdIn(List.of(setId));
    int violations = 0;
    for (ProblemQualityLog log : logs) {
      String explanation =
          log.getV2Explanation() != null ? log.getV2Explanation() : log.getV1Explanation();
      if (!validator.validate(explanation).passed()) {
        violations++;
      }
    }
    return violations;
  }

  /**
   * 지연 로딩 메커니즘을 두 층위로 실증한다: (1) 엔티티 층 {@code isLoaded} — 질문 JSON 미로딩, (2) SQL 층 SELECT — 질문 JSON 컬럼
   * 제외. 인핸스먼트 모드에서만 성립하고, 일반 모드는 전량 로딩(폴백)이다.
   */
  private void verifyLazyState(boolean enhanced) {
    long setId = seed(4);
    em.clear();
    SqlCapture.clear();
    ProblemQualityLog sample = repository.findByProblemSetIdIn(List.of(setId)).get(0);

    PersistenceUnitUtil puu = em.getEntityManagerFactory().getPersistenceUnitUtil();
    boolean questionLoaded = puu.isLoaded(sample, "v1QuestionJson");
    boolean explanationLoaded = puu.isLoaded(sample, "v1Explanation");

    List<String> selects = SqlCapture.selectsFor("problem_quality_log");
    String select = selects.isEmpty() ? "" : selects.get(0);
    boolean sqlHasQuestion =
        select.contains("v1_question_json") || select.contains("v2_question_json");

    System.out.printf(
        "엔티티 상태: v1QuestionJson=%s, v1Explanation=%s%n",
        questionLoaded ? "로딩됨" : "미로딩", explanationLoaded ? "로딩됨" : "미로딩");
    System.out.printf(
        "SELECT에 질문 JSON 컬럼: %s%n", sqlHasQuestion ? "포함(전량 조회)" : "제외(지연 — 전송 대상 축소)");

    assertThat(explanationLoaded).as("해설은 즉시 로딩").isTrue();
    if (enhanced) {
      assertThat(questionLoaded).as("인핸스먼트: 질문 JSON은 접근 전 미로딩(지연)").isFalse();
      assertThat(sqlHasQuestion).as("인핸스먼트: SELECT에서 질문 JSON 컬럼 제외").isFalse();
    } else {
      assertThat(questionLoaded).as("일반: 질문 JSON도 즉시 로딩(폴백)").isTrue();
      assertThat(sqlHasQuestion).as("일반: SELECT에 질문 JSON 컬럼 포함").isTrue();
    }
    em.clear();
  }

  private long seed(long n) {
    long setId = System.nanoTime();
    String largeQuestion = largeJson(questionChars);
    String explanation = validExplanation(explanationChars);
    for (int number = 1; number <= n; number++) {
      em.persist(
          ProblemQualityLog.builder()
              .problemSetId(setId)
              .number(number)
              .v1QuestionJson(largeQuestion)
              .v1Explanation(explanation)
              .build());
    }
    flushAndClear();
    return setId;
  }

  private static String largeJson(int approxChars) {
    return "{\"stem\":\"" + filler(approxChars) + "\",\"selections\":[]}";
  }

  /**
   * {@link ExplanationFormatValidator}의 필수 규칙(정답/오답 헤더·인용·최소 분량)을 모두 만족하는 정형 해설을 만든다. 헤더는 줄 시작에
   * 두고(정규식 {@code ^## …$} 매칭), filler는 본문에만 넣어 형식 통과 → 마킹(쓰기) 없음.
   */
  private static String validExplanation(int approxChars) {
    String head = "- **평가 수준**: 적용\n\n## 정답 선택지\n\n> 정답 선지\n\n정답 근거 해설 본문입니다. ";
    String tail = "\n\n## 오답 선택지\n\n> 오답 선지\n\n오답 근거 해설 본문입니다.\n";
    StringBuilder body = new StringBuilder();
    while (head.length() + body.length() + tail.length() < approxChars) {
      body.append(FILLER);
    }
    return head + body + tail;
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

  private static boolean cpuSupported() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    return bean.isCurrentThreadCpuTimeSupported();
  }

  private static long currentThreadCpuTime() {
    return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
  }

  private static long heapUsedBytes() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
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
