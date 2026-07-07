package com.icc.qasker.quizset;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.QualityStatus;
import com.icc.qasker.quizset.entity.Rationale;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import com.icc.qasker.quizset.support.SqlCapture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 바이트코드 인핸스먼트의 두 쓰기 최적화를 problem_quality_log 마킹 시나리오에서 동시에 검증한다.
 *
 * <ul>
 *   <li><b>전체 필드 비교 최적화(skip-clean)</b>: 미변경 행은 flush 시 스냅샷 deep-compare 없이 건너뛴다 → 변경한 subset만
 *       UPDATE(Statistics.entityUpdateCount == 변경 수).
 *   <li><b>일부 Update(partial column)</b>: 변경 행의 UPDATE SET 절에 quality_status·feedback만 있고, 미변경 대형
 *       컬럼 (rationale JSON 등)은 재기록되지 않는가.
 * </ul>
 */
@TestPropertySource(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.icc.qasker.quizset.support.SqlCapture")
class EnhancementWriteScenarioTest extends JpaIntegrationTestBase {

  @Test
  @DisplayName("5행 중 2건만 마킹 → skip-clean(2건만 UPDATE) + 부분 컬럼 UPDATE(변경 컬럼만) 동시 검증")
  void partialUpdateAndDirtyTrackingInOneScenario() {
    for (int number = 1; number <= 5; number++) {
      em.persist(qualityRow(100L, number));
    }
    flushAndClear();

    statistics().clear();
    SqlCapture.clear();

    // 5행 중 2·4번만 마킹 — quality_status·feedback 두 컬럼만 변경
    for (int number : new int[] {2, 4}) {
      repositoryFind(100L, number).applyVerdict(QualityStatus.BELOW_THRESHOLD, "정답 근거 불명확");
    }
    em.flush();

    // (A) 전체 필드 비교 최적화: 미변경 3건은 건너뜀 → UPDATE 2건뿐
    assertThat(statistics().getEntityUpdateCount())
        .as("skip-clean: 변경한 2건만 UPDATE, 미변경 3건은 건너뜀")
        .isEqualTo(2);

    // (B) 일부 Update: UPDATE SET 절에 변경 컬럼만
    List<String> updates = SqlCapture.updatesFor("problem_quality_log");
    assertThat(updates).as("problem_quality_log UPDATE 2건").hasSize(2);
    assertThat(updates)
        .allSatisfy(
            sql -> {
              assertThat(sql).as("변경 컬럼 포함").contains("quality_status").contains("feedback");
              assertThat(sql)
                  .as("미변경 대형 컬럼(rationale JSON)은 UPDATE에 포함되지 않아야 함")
                  .doesNotContain("rationale");
            });
  }

  private ProblemQualityLog repositoryFind(long setId, int number) {
    return em.createQuery(
            "select q from ProblemQualityLog q where q.problemSetId = :sid and q.number = :n",
            ProblemQualityLog.class)
        .setParameter("sid", setId)
        .setParameter("n", number)
        .getSingleResult();
  }

  private ProblemQualityLog qualityRow(long setId, int number) {
    return ProblemQualityLog.builder()
        .problemSetId(setId)
        .number(number)
        .qualityStatus(QualityStatus.OK)
        .rationale(
            new Rationale(
                new Rationale.SourceAnchor(1, "1장 정규화", "제3정규형은 이행 종속을 제거한다. ".repeat(10)),
                "정규형 이해",
                "UNDERSTAND",
                0.5,
                "구성전략 설명 ".repeat(10),
                "지시 없음",
                0.8,
                new Rationale.SelfChecks(true, true, true, true),
                null,
                null))
        .build();
  }
}
