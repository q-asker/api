package com.icc.qasker.quizset;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.QualityStatus;
import com.icc.qasker.quizset.entity.Rationale;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * rationale(JSON 값객체)가 problem_quality_log에 저장·조회되는지 검증한다. problem은 순수 서빙만 책임지고 생성 근거·품질 판정은 이 로그로
 * 분리됐다. 객관형(selfChecks)·서술형(modelAnswerBasis) 유형별 필드가 JSON 왕복에서 보존됨을 확인한다.
 */
class RationalePersistenceTest extends JpaIntegrationTestBase {

  @Test
  @DisplayName("객관형 rationale(selfChecks 포함)이 JSON으로 저장·복원된다")
  void objectiveRationaleRoundTrips() {
    Rationale rationale =
        new Rationale(
            new Rationale.SourceAnchor(3, "2장 정규화", "제3정규형은 이행 종속을 제거한다"),
            "정규형 개념 이해",
            "UNDERSTAND",
            0.4,
            "핵심 정의를 물어보는 개념 확인 문항",
            "지시 없음",
            0.9,
            new Rationale.SelfChecks(true, true, true, true),
            null,
            null);
    ProblemQualityLog row =
        ProblemQualityLog.builder()
            .problemSetId(10L)
            .number(1)
            .rationale(rationale)
            .qualityStatus(QualityStatus.OK)
            .build();
    em.persist(row);
    Long id = row.getId();
    flushAndClear();

    ProblemQualityLog found = em.find(ProblemQualityLog.class, id);
    assertThat(found.getRationale()).isNotNull();
    assertThat(found.getRationale().sourceAnchor().page()).isEqualTo(3);
    assertThat(found.getRationale().constructionStrategy()).contains("개념 확인");
    assertThat(found.getRationale().selfChecks().singleCorrectAnswer()).isTrue();
    assertThat(found.getRationale().modelAnswerBasis()).isNull();
    assertThat(found.getQualityStatus()).isEqualTo(QualityStatus.OK);
  }

  @Test
  @DisplayName("서술형 rationale(modelAnswerBasis·rubricConsistency)이 JSON으로 저장·복원된다")
  void essayRationaleRoundTrips() {
    Rationale rationale =
        new Rationale(
            new Rationale.SourceAnchor(5, "3장 트랜잭션", "ACID의 원자성"),
            "ACID 서술",
            "ANALYZE",
            0.7,
            "원문 근거로 논증을 요구하는 서술형",
            "지시 없음",
            0.8,
            null,
            "원문 3장 ACID 정의에 근거",
            true);
    ProblemQualityLog row =
        ProblemQualityLog.builder().problemSetId(11L).number(1).rationale(rationale).build();
    em.persist(row);
    Long id = row.getId();
    flushAndClear();

    ProblemQualityLog found = em.find(ProblemQualityLog.class, id);
    assertThat(found.getRationale().modelAnswerBasis()).contains("ACID");
    assertThat(found.getRationale().rubricConsistency()).isTrue();
    assertThat(found.getRationale().selfChecks()).isNull();
  }

  @Test
  @DisplayName("rationale 없는 로그 행은 rationale=NULL(레거시 대상 제외)")
  void legacyRowHasNullRationale() {
    ProblemQualityLog row = ProblemQualityLog.builder().problemSetId(12L).number(1).build();
    em.persist(row);
    Long id = row.getId();
    flushAndClear();

    ProblemQualityLog found = em.find(ProblemQualityLog.class, id);
    assertThat(found.getRationale()).isNull();
    assertThat(found.getQualityStatus()).isNull();
  }
}
