package com.icc.qasker.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.SelectionData;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/**
 * 비정규화 전/후 쿼리 수 비교 테스트.
 *
 * <p>테스트 데이터: ProblemSet 1개, Problem 5개 (각 SelectionData 4개, ReferencedPage 2개)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountSnapshotTest {

  @TestConfiguration
  @EnableJpaAuditing
  @EnableJpaRepositories(basePackages = "com.icc.qasker.quiz.repository")
  @EntityScan(basePackages = "com.icc.qasker")
  static class JpaAuditingConfig {

  }

  @Autowired
  private EntityManagerFactory emf;
  @Autowired
  private ProblemSetRepository problemSetRepository;
  @Autowired
  private ProblemRepository problemRepository;
  @Autowired
  private EntityManager em;

  private Long savedProblemSetId;

  @BeforeEach
  void setUp() {
    ProblemSet problemSet =
      ProblemSet.builder()
        .userId("test-user")
        .generationStatus(GenerationStatus.COMPLETED)
        .quizType(QuizType.MULTIPLE)
        .totalQuizCount(5)
        .sessionId("test-session-" + System.nanoTime())
        .build();
    problemSetRepository.save(problemSet);
    savedProblemSetId = problemSet.getId();

    for (int i = 1; i <= 5; i++) {
      ProblemId problemId = ProblemId.builder().problemSetId(savedProblemSetId).number(i).build();
      Problem problem =
        Problem.builder().id(problemId).title("Problem " + i).problemSet(problemSet).build();

      List<SelectionData> selections =
        List.of(
          new SelectionData("Option 1", true),
          new SelectionData("Option 2", false),
          new SelectionData("Option 3", false),
          new SelectionData("Option 4", false));

      problem.bindChildren(selections, "Explanation " + i, List.of(1, i + 1));
      em.persist(problem);
    }

    em.flush();
    em.clear();
  }

  /**
   * [AFTER 비정규화] ProblemSetServiceImpl.getProblemSet() 패턴.
   *
   * <ul>
   *   <li>1: ProblemSet 조회
   *   <li>1: Problem 목록 조회 (selections는 JSON 컬럼 — 추가 쿼리 없음)
   * </ul>
   */
  @Test
  @DisplayName("[AFTER] getProblemSet query count")
  void getProblemSet_after() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    ProblemSet ps = problemSetRepository.findById(savedProblemSetId).orElseThrow();
    ps.getProblems().forEach(p -> p.getSelections().size());

    long queryCount = stats.getPrepareStatementCount();
    System.out.printf(
      "%n[AFTER]  getProblemSet query count: %d (5 problems)%n" + "         [BEFORE] was: 7%n",
      queryCount);

    assertThat(queryCount).isLessThan(7);
  }

  /**
   * [AFTER 비정규화] ExplanationServiceImpl.getExplanationByProblemSetId() 패턴.
   *
   * <p>Before: 7쿼리 → After 목표: 1쿼리
   *
   * <ul>
   *   <li>1: Problem 목록 조회 (explanationContent/referencedPages는 JSON 컬럼 — 추가 쿼리 없음)
   * </ul>
   */
  @Test
  @DisplayName("[AFTER] getExplanation query count")
  void getExplanation_after() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    List<Problem> problems = problemRepository.findByIdProblemSetId(savedProblemSetId);
    problems.forEach(
      p -> {
        p.getExplanationContent();
        p.getReferencedPages().size();
      });

    long queryCount = stats.getPrepareStatementCount();
    System.out.printf(
      "%n[AFTER]  getExplanation query count: %d (5 problems)%n" + "         [BEFORE] was: 11%n",
      queryCount);

    assertThat(queryCount).isEqualTo(1);
  }
}
