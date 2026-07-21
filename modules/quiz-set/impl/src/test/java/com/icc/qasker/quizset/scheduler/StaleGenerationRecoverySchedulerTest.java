package com.icc.qasker.quizset.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StaleGenerationRecoverySchedulerTest extends JpaIntegrationTestBase {

  @Autowired private ProblemSetRepository problemSetRepository;
  @Autowired private ProblemRepository problemRepository;

  @Test
  @DisplayName("10분 초과 방치 세트 중 FAILED/GENERATING만 삭제되고 COMPLETED는 생존한다")
  void scheduler_keeps_completed_sets() {
    persistSet("s-failed", GenerationStatus.FAILED);
    persistSet("s-generating", GenerationStatus.GENERATING);
    persistSet("s-completed", GenerationStatus.COMPLETED);
    flushAndClear();
    backdateAllSets(Instant.now().minus(20, ChronoUnit.MINUTES));

    scheduler().deleteStaleProblemSets();
    flushAndClear();

    assertThat(problemSetRepository.findAll())
        .extracting(ProblemSet::getGenerationStatus)
        .containsExactly(GenerationStatus.COMPLETED);
  }

  @Test
  @DisplayName("stale 세트 삭제 시 자식 Problem도 함께 삭제되고 생존 세트의 자식은 유지된다")
  void scheduler_deletes_child_problems_of_stale_sets() {
    ProblemSet stale = persistSet("s-failed", GenerationStatus.FAILED);
    ProblemSet survivor = persistSet("s-completed", GenerationStatus.COMPLETED);
    persistProblem(stale, 1);
    persistProblem(survivor, 1);
    flushAndClear();
    backdateAllSets(Instant.now().minus(20, ChronoUnit.MINUTES));

    scheduler().deleteStaleProblemSets();
    flushAndClear();

    assertThat(problemRepository.findByIdProblemSetId(stale.getId())).isEmpty();
    assertThat(problemRepository.findByIdProblemSetId(survivor.getId())).hasSize(1);
  }

  private StaleGenerationRecoveryScheduler scheduler() {
    return new StaleGenerationRecoveryScheduler(
        new StaleGenerationRecoveryService(problemSetRepository, problemRepository));
  }

  private ProblemSet persistSet(String sessionId, GenerationStatus status) {
    ProblemSet set =
        ProblemSet.builder()
            .title("세트")
            .sessionId(sessionId)
            .fileUrl("http://file")
            .totalQuizCount(1)
            .generationStatus(status)
            .build();
    em.persist(set);
    return set;
  }

  private void persistProblem(ProblemSet set, int number) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().number(number).build())
            .title("문항" + number)
            .problemSet(set)
            .build();
    problem.bindQuizData(List.of(new Selection("보기", null, true)), List.of(1));
    em.persist(problem);
  }

  /**
   * @CreatedDate는 저장 시각으로 고정되므로 벌크 UPDATE로 과거 시각으로 되돌린다.
   */
  private void backdateAllSets(Instant createdAt) {
    em.createQuery("update ProblemSet p set p.createdAt = :createdAt")
        .setParameter("createdAt", createdAt)
        .executeUpdate();
    em.clear();
  }
}
