package com.icc.qasker.quizset.service.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.dto.ExplanationReviewResult;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 해설 형식 검증(정규식)의 dirty tracking을 검증한다. 형식 미달 문항만 review에 마킹되고 통과분은 무변경이며, 품질 로그 UPDATE 수가 미달 수와
 * 일치한다. 서빙 problem은 로드하지 않는다(로그 자기완결).
 */
class ExplanationReviewServiceTest extends JpaIntegrationTestBase {

  @Autowired private ProblemQualityLogRepository qualityLogRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private ExplanationReviewServiceImpl service;

  private static final String VALID_EXPLANATION =
      """
      - **평가 수준**: 적용

      ---

      ## 정답 선택지

      > 정답 선지

      이 선지가 정답인 이유를 충분히 서술하는 해설 본문입니다. 최소 분량 요건을 넘기기 위해 개념과 근거를 상세히 설명하고 추가 문장을 덧붙입니다.

      ---

      ## 오답 선택지

      > 오답 선지

      이 선지가 오답인 이유를 설명합니다.
      """;

  @BeforeEach
  void initService() {
    service =
        new ExplanationReviewServiceImpl(
            qualityLogRepository,
            new ExplanationFormatValidator(),
            new TransactionTemplate(transactionManager));
  }

  @Test
  @DisplayName("형식 미달 해설만 review에 마킹되고 통과분은 무변경 — UPDATE 수 = 미달 수")
  void marksOnlyMalformedSubset() {
    long setId = 1000L;
    persistQuality(setId, 1, VALID_EXPLANATION);
    persistQuality(setId, 2, "짧고 헤더 없는 해설"); // 형식 미달
    persistQuality(setId, 3, VALID_EXPLANATION);
    flushAndClear();

    statistics().clear();
    ExplanationReviewResult result = service.review(setId);
    flushAndClear();

    assertThat(result.reviewedCount()).isEqualTo(3);
    assertThat(result.violationCount()).isEqualTo(1);
    // 미달 1건만 UPDATE(통과분 무변경 → skip-clean)
    assertThat(statistics().getEntityUpdateCount()).isEqualTo(1);

    assertThat(quality(setId, 2).getReview()).contains("헤더 누락");
    assertThat(quality(setId, 1).getReview()).isNull();
  }

  @Test
  @DisplayName("개선본(v2) 해설이 있으면 v2를 우선 검증한다")
  void prefersV2Explanation() {
    long setId = 2000L;
    ProblemQualityLog row =
        ProblemQualityLog.builder()
            .problemSetId(setId)
            .number(1)
            .v1Explanation(VALID_EXPLANATION) // v1은 정형
            .v2Explanation("짧은 개선본") // v2는 미달 → v2가 우선되어 미달 판정
            .build();
    em.persist(row);
    flushAndClear();

    ExplanationReviewResult result = service.review(setId);
    flushAndClear();

    assertThat(result.violationCount()).isEqualTo(1);
    assertThat(quality(setId, 1).getReview()).isNotNull();
  }

  private void persistQuality(long setId, int number, String explanation) {
    em.persist(
        ProblemQualityLog.builder()
            .problemSetId(setId)
            .number(number)
            .v1Explanation(explanation)
            .build());
  }

  private ProblemQualityLog quality(long setId, int number) {
    return qualityLogRepository.findByProblemSetIdAndNumber(setId, number).orElseThrow();
  }
}
