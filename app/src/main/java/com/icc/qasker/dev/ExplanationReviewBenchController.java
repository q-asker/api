package com.icc.qasker.dev;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.service.quality.ExplanationFormatValidator;
import com.sun.management.OperatingSystemMXBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongToIntFunction;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 전용 explanation-review 리소스 벤치 훅(인증 불필요, {@code @Profile("local")}). 실 MySQL에 큰 질문 JSON(지연 로딩
 * 대상)을 가진 품질 로그 N행을 시드하고, 서비스 읽기 경로({@code ExplanationReviewServiceImpl}와 동일한 로드→검증→미달분 마킹)를 K회 반복
 * 호출해 앱에 부하를 만든다. 앱 프로세스 CPU-time·RAM은 외부(ps/actuator)에서 계측한다. 계측 OFF/ON 빌드를 각각 띄워 비교하고, 측정 후
 * cleanup으로 원복한다. HTTP 호출은 seed/run/cleanup 3회뿐이며 반복은 서버 측 루프로 처리해 레이트리밋을 피한다.
 *
 * <p>app 모듈은 quiz-set-api(서비스 인터페이스)가 전이 노출되지 않으므로, api를 참조하지 않고 impl의 리포지토리·검증기로 읽기 경로를 직접 재현한다.
 */
@Profile("local")
@RestController
@RequestMapping("/dev/bench/explanation-review")
public class ExplanationReviewBenchController {

  private static final String FILLER = "가나다라마바사아자차 ";

  private final ProblemQualityLogRepository repository;
  private final ExplanationFormatValidator validator;
  private final TransactionTemplate transactionTemplate;

  @PersistenceContext private EntityManager em;

  public ExplanationReviewBenchController(
      ProblemQualityLogRepository repository,
      ExplanationFormatValidator validator,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.validator = validator;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /** 현재 실행 바이너리가 인핸스먼트 계측 빌드인지(질문 JSON 지연 로딩 활성) 반환한다. */
  @GetMapping("/mode")
  public Map<String, Object> mode() {
    boolean enhanced =
        PersistentAttributeInterceptable.class.isAssignableFrom(ProblemQualityLog.class);
    return Map.of("enhanced", enhanced);
  }

  /** 세트에 큰 질문 JSON + 정형 해설(형식 통과 → 마킹 없음, 읽기 격리)을 가진 품질 로그 N행을 시드한다. */
  @PostMapping("/seed")
  public Map<String, Object> seed(
      @RequestParam long setId,
      @RequestParam(defaultValue = "1000") int n,
      @RequestParam(defaultValue = "4000") int questionChars,
      @RequestParam(defaultValue = "600") int explanationChars) {
    String question = largeJson(questionChars);
    String explanation = validExplanation(explanationChars);
    transactionTemplate.executeWithoutResult(
        status -> {
          em.createQuery("delete from ProblemQualityLog q where q.problemSetId = :sid")
              .setParameter("sid", setId)
              .executeUpdate();
          for (int number = 1; number <= n; number++) {
            em.persist(
                ProblemQualityLog.builder()
                    .problemSetId(setId)
                    .number(number)
                    .v1QuestionJson(question)
                    .v1Explanation(explanation)
                    .build());
            if (number % 100 == 0) {
              em.flush();
              em.clear();
            }
          }
          em.flush();
          em.clear();
        });
    return Map.of("seeded", n, "setId", setId);
  }

  /** 현재 방식: 엔티티 로드(계측 ON이면 질문 JSON 지연) → 검증 → 미달분 더티 마킹(per-row UPDATE). */
  @PostMapping("/run")
  public Map<String, Object> run(
      @RequestParam long setId, @RequestParam(defaultValue = "1") int iterations) {
    return measure(setId, iterations, this::reviewOnce);
  }

  /** 대안: 프로젝션(해설 컬럼만 SELECT — 인핸스먼트 불필요) → 검증 → 미달분 단일 CASE UPDATE(벌크·한 쿼리). */
  @PostMapping("/run-projection")
  public Map<String, Object> runProjection(
      @RequestParam long setId, @RequestParam(defaultValue = "1") int iterations) {
    return measure(setId, iterations, this::reviewProjectionOnce);
  }

  /** 지정 로직을 K회 돌며 프로세스 CPU·힙·DB global_status 델타를 잰다. */
  private Map<String, Object> measure(long setId, int iterations, LongToIntFunction op) {
    OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    Runtime rt = Runtime.getRuntime();

    Map<String, Long> db0 = dbStatus();
    long heapBefore = rt.totalMemory() - rt.freeMemory();
    long cpu0 = os.getProcessCpuTime();
    long t0 = System.nanoTime();
    int reviewedPerCall = 0;
    for (int i = 0; i < iterations; i++) {
      reviewedPerCall = op.applyAsInt(setId);
    }
    long wallNanos = System.nanoTime() - t0;
    long cpuNanos = os.getProcessCpuTime() - cpu0;
    long heapAfter = rt.totalMemory() - rt.freeMemory();
    Map<String, Long> db1 = dbStatus();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("iterations", iterations);
    out.put("reviewedPerCall", reviewedPerCall);
    out.put("totalReviews", (long) iterations * reviewedPerCall);
    out.put("wallMicros", wallNanos / 1_000);
    out.put("appCpuMicros", cpuNanos / 1_000);
    out.put("heapBeforeBytes", heapBefore);
    out.put("heapAfterBytes", heapAfter);
    out.put("heapDeltaBytes", heapAfter - heapBefore);
    // DB(글로벌 상태) 델타 — 배치 전후 차이. 글로벌 카운터라 타 커넥션 활동이 섞일 수 있음(측정 시 앱만 부하).
    db1.forEach((k, v) -> out.put("db_" + k, v - db0.getOrDefault(k, 0L)));
    return out;
  }

  /** performance_schema.global_status에서 전송·InnoDB I/O 카운터를 읽는다(앱 자기 커넥션). */
  private Map<String, Long> dbStatus() {
    return transactionTemplate.execute(
        status -> {
          @SuppressWarnings("unchecked")
          List<Object[]> rows =
              em.createNativeQuery(
                      "SELECT VARIABLE_NAME, VARIABLE_VALUE FROM performance_schema.global_status"
                          + " WHERE VARIABLE_NAME IN ('Bytes_sent','Innodb_data_read',"
                          + "'Innodb_buffer_pool_reads','Innodb_rows_read')")
                  .getResultList();
          Map<String, Long> m = new LinkedHashMap<>();
          for (Object[] r : rows) {
            m.put(String.valueOf(r[0]), Long.parseLong(String.valueOf(r[1])));
          }
          return m;
        });
  }

  /** {@code ExplanationReviewServiceImpl.review}와 동일: 세트 로그 로드 → 해설 검증 → 미달분만 마킹. */
  private int reviewOnce(long setId) {
    Integer reviewed =
        transactionTemplate.execute(
            status -> {
              List<ProblemQualityLog> logs = repository.findByProblemSetIdIn(List.of(setId));
              for (ProblemQualityLog log : logs) {
                String explanation =
                    log.getV2Explanation() != null
                        ? log.getV2Explanation()
                        : log.getV1Explanation();
                ExplanationFormatValidator.Result result = validator.validate(explanation);
                if (!result.passed()) {
                  log.markExplanationReview(result.summary());
                }
              }
              return logs.size();
            });
    return reviewed == null ? 0 : reviewed;
  }

  /**
   * 대안 경로: 프로젝션으로 해설 컬럼만 SELECT(질문 JSON 미조회 — 인핸스먼트 불필요) → 검증 → 미달분을 단일 {@code UPDATE ... SET
   * review = CASE id ... END WHERE id IN (...)} 한 쿼리(벌크)로 반영한다. 엔티티 로드·더티체크 없이 처리한다.
   */
  private int reviewProjectionOnce(long setId) {
    Integer reviewed =
        transactionTemplate.execute(
            status -> {
              @SuppressWarnings("unchecked")
              List<Object[]> rows =
                  em.createQuery(
                          "select q.id, q.v2Explanation, q.v1Explanation from ProblemQualityLog q"
                              + " where q.problemSetId = :sid")
                      .setParameter("sid", setId)
                      .getResultList();
              List<Long> failIds = new ArrayList<>();
              List<String> failSummaries = new ArrayList<>();
              for (Object[] r : rows) {
                String explanation = r[1] != null ? (String) r[1] : (String) r[2];
                ExplanationFormatValidator.Result result = validator.validate(explanation);
                if (!result.passed()) {
                  failIds.add((Long) r[0]);
                  failSummaries.add(result.summary());
                }
              }
              if (!failIds.isEmpty()) {
                StringBuilder sql =
                    new StringBuilder("update problem_quality_log set review = case id");
                for (int i = 0; i < failIds.size(); i++) {
                  sql.append(" when ").append(failIds.get(i)).append(" then ?").append(i + 1);
                }
                sql.append(" end where id in (");
                for (int i = 0; i < failIds.size(); i++) {
                  sql.append(i == 0 ? "" : ",").append(failIds.get(i));
                }
                sql.append(")");
                var q = em.createNativeQuery(sql.toString());
                for (int i = 0; i < failSummaries.size(); i++) {
                  q.setParameter(i + 1, failSummaries.get(i));
                }
                q.executeUpdate();
              }
              return rows.size();
            });
    return reviewed == null ? 0 : reviewed;
  }

  /** 시드 데이터를 삭제해 원복한다. */
  @PostMapping("/cleanup")
  public Map<String, Object> cleanup(@RequestParam long setId) {
    Integer deleted =
        transactionTemplate.execute(
            status ->
                em.createQuery("delete from ProblemQualityLog q where q.problemSetId = :sid")
                    .setParameter("sid", setId)
                    .executeUpdate());
    return Map.of("deleted", deleted == null ? 0 : deleted);
  }

  private static String largeJson(int approxChars) {
    return "{\"stem\":\"" + filler(approxChars) + "\",\"selections\":[]}";
  }

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
    StringBuilder sb = new StringBuilder();
    while (sb.length() < approxChars) {
      sb.append(FILLER);
    }
    return sb.substring(0, approxChars);
  }
}
