package com.icc.qasker.quizset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.quizset.dto.QualityReviewResult;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import com.icc.qasker.quizset.service.quality.QualityReviewServiceImpl;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pass 2 재검토의 dirty tracking·레거시 제외를 검증한다. 미달 subset만 problem_quality_log에 마킹되고 통과분은 무변경이며, flush
 * UPDATE 수가 미달 수와 일치함을 Statistics로 확인한다(SC-003). problem(서빙)은 건드리지 않는다.
 */
class QualityReviewServiceTest extends JpaIntegrationTestBase {

  @Autowired private ProblemSetRepository problemSetRepository;
  @Autowired private ProblemQualityLogRepository qualityLogRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private QualityVerifier verifier;
  private QualityReviewServiceImpl service;

  @BeforeEach
  void initService() {
    verifier = mock(QualityVerifier.class);
    service =
        new QualityReviewServiceImpl(
            problemSetRepository,
            qualityLogRepository,
            verifier,
            new TransactionTemplate(transactionManager));
  }

  @Test
  @DisplayName("미달 subset만 마킹되고 통과분은 무변경 — 품질 로그 UPDATE 수 = 미달 수")
  void marksOnlyBelowThresholdSubset() {
    ProblemSet set = persistSet("sess-review");
    persistProblemWithQuality(set, 1, "GOOD 문항 1");
    persistProblemWithQuality(set, 2, "BAD 문항 2");
    persistProblemWithQuality(set, 3, "GOOD 문항 3");
    flushAndClear();

    when(verifier.verify(any()))
        .thenAnswer(
            inv ->
                ((QualityVerificationRequest) inv.getArgument(0)).question().contains("BAD")
                    ? QualityVerdict.below("정답 근거 불명확")
                    : QualityVerdict.pass());

    statistics().clear();
    service.review(List.of(set.getId()));
    QualityReviewResult result = service.latestResult(set.getId()).orElseThrow();
    flushAndClear();

    assertThat(result.reviewedCount()).isEqualTo(3);
    assertThat(result.belowThresholdCount()).isEqualTo(1);
    // 미달 1건만 UPDATE(통과분 무변경 → skip-clean)
    assertThat(statistics().getEntityUpdateCount()).isEqualTo(1);

    ProblemQualityLog bad =
        qualityLogRepository.findByProblemSetIdAndNumber(set.getId(), 2).orElseThrow();
    // Pass-2(질문 재검증)는 v2Feedback만 마킹하고 review(해설 형식 검증)는 건드리지 않는다
    assertThat(bad.getV2Feedback()).isEqualTo("정답 근거 불명확");
    assertThat(bad.getReview()).isNull();
    // 통과 문항은 재검토가 건드리지 않아 v2Feedback이 null로 유지
    assertThat(
            qualityLogRepository
                .findByProblemSetIdAndNumber(set.getId(), 1)
                .orElseThrow()
                .getV2Feedback())
        .isNull();
  }

  @Test
  @DisplayName("로드 문항 수를 바꿔도 UPDATE 수는 미달 고정 수와 일치한다")
  void updateCountTracksBelowThresholdNotLoadCount() {
    ProblemSet set = persistSet("sess-review2");
    for (int i = 1; i <= 5; i++) {
      persistProblemWithQuality(set, i, i == 3 ? "BAD 문항" : "GOOD 문항 " + i);
    }
    flushAndClear();

    when(verifier.verify(any()))
        .thenAnswer(
            inv ->
                ((QualityVerificationRequest) inv.getArgument(0)).question().contains("BAD")
                    ? QualityVerdict.below("미달")
                    : QualityVerdict.pass());

    statistics().clear();
    service.review(List.of(set.getId()));
    QualityReviewResult result = service.latestResult(set.getId()).orElseThrow();
    flushAndClear();

    assertThat(result.reviewedCount()).isEqualTo(5);
    assertThat(result.belowThresholdCount()).isEqualTo(1);
    assertThat(statistics().getEntityUpdateCount()).isEqualTo(1);
  }

  private ProblemSet persistSet(String sessionId) {
    ProblemSet set =
        ProblemSet.builder()
            .sessionId(sessionId)
            .title("세트")
            .userId("user-1")
            .totalQuizCount(5)
            .quizType(QuizType.MULTIPLE)
            .fileUrl("file-url")
            .build();
    em.persist(set);
    return set;
  }

  private void persistProblemWithQuality(ProblemSet set, int number, String title) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().number(number).build())
            .title(title)
            .problemSet(set)
            .build();
    problem.bindQuizData(List.of(new Selection("보기", null, true)), List.of(1));
    em.persist(problem);
    // Pass-2는 로그의 질문 JSON에서 검증 요청을 재구성한다(옵션 A). stem에 제목을 담아 검증기 분기가 동작하게 한다.
    em.persist(
        ProblemQualityLog.builder()
            .problemSetId(set.getId())
            .number(number)
            .v1QuestionJson(
                "{\"stem\":\""
                    + title
                    + "\",\"selections\":[{\"content\":\"보기\",\"correct\":true}]}")
            .build());
  }
}
