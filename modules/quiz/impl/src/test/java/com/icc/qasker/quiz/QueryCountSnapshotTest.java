package com.icc.qasker.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;
import com.icc.qasker.quiz.entity.Selection;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
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
 * 비정규화 전/후 쿼리 수 비교를 위한 Before 스냅샷 테스트.
 *
 * <p>N+1은 JPA(Hibernate) 레벨 현상이므로 H2와 MySQL의 쿼리 수는 동일하다.
 *
 * <p>테스트 데이터: ProblemSet 1개, Problem 3개 (각 Selection 4개, Explanation 1개, ReferencedPage 2개)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountSnapshotTest {

  // @DataJpaTest는 JPA 슬라이스 컨텍스트라 @EnableJpaAuditing이 없음 → 별도 추가
  // @EnableJpaRepositories, @EntityScan: IntelliJ 정적 분석용 — 실제 스캔은 @DataJpaTest가 처리
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
        .totalQuizCount(3)
        .sessionId("test-session-" + System.nanoTime())
        .build();
    problemSetRepository.save(problemSet);
    savedProblemSetId = problemSet.getId();

    // Problem 3개 생성 (각각 Selection 4개, Explanation 1개, ReferencedPage 2개)
    for (int i = 1; i <= 3; i++) {
      ProblemId problemId = ProblemId.builder().problemSetId(savedProblemSetId).number(i).build();
      Problem problem =
        Problem.builder().id(problemId).title("문제 " + i).problemSet(problemSet).build();

      List<Selection> selections =
        List.of(
          Selection.builder().content("보기 1").correct(true).problem(problem).build(),
          Selection.builder().content("보기 2").correct(false).problem(problem).build(),
          Selection.builder().content("보기 3").correct(false).problem(problem).build(),
          Selection.builder().content("보기 4").correct(false).problem(problem).build());

      Explanation explanation = Explanation.of("해설 " + i, problem);

      List<ReferencedPage> pages =
        List.of(ReferencedPage.of(1, problem), ReferencedPage.of(i + 1, problem));

      problem.bindChildren(new ArrayList<>(selections), explanation, new ArrayList<>(pages));
      em.persist(problem);
    }

    em.flush();
    em.clear(); // 영속성 컨텍스트 초기화 → 이후 조회는 반드시 DB 히트
  }

  /**
   * [BEFORE 비정규화] ProblemSetServiceImpl.getProblemSet() 패턴.
   *
   * <p>예상 쿼리 수: batch_fetch_size 적용 시 3개
   *
   * <ul>
   *   <li>1: ProblemSet 조회
   *   <li>1: Problem 목록 Lazy 로드
   *   <li>1: Selection 배치 Lazy 로드 (default_batch_fetch_size로 N+1 → 1)
   * </ul>
   *
   * <p>비정규화 After 목표: 1개
   */
  @Test
  @DisplayName("[BEFORE] getProblemSet query count")
  void getProblemSet_before() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    // ProblemSetServiceImpl 로직 재현
    ProblemSet ps = problemSetRepository.findById(savedProblemSetId).orElseThrow();
    ps.getProblems().forEach(p -> p.getSelections().size());

    long queryCount = stats.getPrepareStatementCount();
    System.out.printf(
      "%n[BEFORE] getProblemSet query count: %d (3 problems)%n"
        + "         [AFTER]  target after denormalization: 1%n",
      queryCount);

    // Before는 2개 이상임을 확인 (N+1 또는 배치 쿼리 발생)
    assertThat(queryCount).isGreaterThan(1);
  }

  /**
   * [BEFORE 비정규화] ExplanationServiceImpl.getExplanationByProblemSetId() 패턴.
   *
   * <p>예상 쿼리 수: batch_fetch_size 적용 시 3개
   *
   * <ul>
   *   <li>1: Problem 목록 조회
   *   <li>1: Explanation 배치 Lazy 로드
   *   <li>1: ReferencedPage 배치 Lazy 로드
   * </ul>
   *
   * <p>비정규화 After 목표: 1개
   */
  @Test
  @DisplayName("[BEFORE] getExplanation query count")
  void getExplanation_before() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    // ExplanationServiceImpl 로직 재현
    List<Problem> problems = problemRepository.findByIdProblemSetId(savedProblemSetId);
    problems.forEach(
      p -> {
        if (p.getExplanation() != null) {
          p.getExplanation().getContent();
        }
        p.getReferencedPages().size();
      });

    long queryCount = stats.getPrepareStatementCount();
    System.out.printf(
      "%n[BEFORE] getExplanation query count: %d (3 problems)%n"
        + "         [AFTER]  target after denormalization: 1%n",
      queryCount);

    // Before는 2개 이상임을 확인
    assertThat(queryCount).isGreaterThan(1);
  }
}
