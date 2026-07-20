package com.icc.qasker.quizset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
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

/** 품질 로그가 없는 세트가 재검토 대상에서 제외됨을 검증한다. 단건은 400, 일괄은 스킵된다. */
class QualityReviewLegacyTest extends JpaIntegrationTestBase {

  @Autowired private ProblemSetRepository problemSetRepository;
  @Autowired private ProblemQualityLogRepository qualityLogRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private QualityVerifier verifier;
  private QualityReviewServiceImpl service;

  @BeforeEach
  void initService() {
    verifier = mock(QualityVerifier.class);
    when(verifier.verify(any())).thenReturn(QualityVerdict.pass());
    service =
        new QualityReviewServiceImpl(
            problemSetRepository,
            qualityLogRepository,
            verifier,
            new TransactionTemplate(transactionManager));
  }

  @Test
  @DisplayName("품질 로그 없는 세트 단건 재검토는 400으로 거부된다")
  void legacySetRejectedOnSingle() {
    ProblemSet set = persistSet("legacy-1");
    persistProblem(set, 1); // 품질 로그 없음 → 대상 없음
    flushAndClear();

    assertThatThrownBy(() -> service.review(List.of(set.getId())))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getMessage())
                    .isEqualTo(ExceptionMessage.QUALITY_REVIEW_NO_TARGET.getMessage()));
  }

  @Test
  @DisplayName("일괄 재검토는 품질 로그 없는 세트를 건너뛰고 품질 로그 보유 세트만 결과에 포함한다")
  void bulkSkipsLegacySets() {
    ProblemSet legacy = persistSet("legacy-2");
    persistProblem(legacy, 1); // 품질 로그 없음
    ProblemSet valid = persistSet("valid-1");
    persistProblem(valid, 1);
    persistQuality(valid.getId(), 1);
    flushAndClear();

    service.review(List.of(legacy.getId(), valid.getId()));

    // 품질 로그 없는 legacy 세트는 결과에 없고, 보유 세트만 집계된다.
    assertThat(service.latestResult(legacy.getId())).isEmpty();
    assertThat(service.latestResult(valid.getId()))
        .hasValueSatisfying(
            r -> {
              assertThat(r.problemSetId()).isEqualTo(valid.getId());
              assertThat(r.reviewedCount()).isEqualTo(1);
            });
  }

  private ProblemSet persistSet(String sessionId) {
    ProblemSet set =
        ProblemSet.builder()
            .sessionId(sessionId)
            .title("세트")
            .userId("user-1")
            .totalQuizCount(1)
            .quizType(QuizType.MULTIPLE)
            .fileUrl("file-url")
            .build();
    em.persist(set);
    return set;
  }

  private void persistProblem(ProblemSet set, int number) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().number(number).build())
            .title("문항 " + number)
            .problemSet(set)
            .build();
    problem.bindQuizData(List.of(new Selection("보기", null, true)), List.of(1));
    em.persist(problem);
  }

  private void persistQuality(Long setId, int number) {
    em.persist(ProblemQualityLog.builder().problemSetId(setId).number(number).build());
  }
}
