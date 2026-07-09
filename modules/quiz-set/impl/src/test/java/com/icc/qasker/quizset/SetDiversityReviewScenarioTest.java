package com.icc.qasker.quizset;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import com.icc.qasker.quizset.support.SqlCapture;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * S3 — 세트 내 상대 평가(다양성·중복) 시나리오. "전량 로드 → 일부 업데이트"가 원리적으로 정당한 부류를 실증한다: 중복은 문항끼리 교차 비교해야 판정되므로 대상만 콕
 * 집어 로드할 수 없다.
 *
 * <p>비교 기준은 <b>실제 문항 내용(Problem.title)</b>이다. 서빙(Problem)만 읽고 품질 로그는 대상만 마킹한다.
 *
 * <ul>
 *   <li><b>서빙(Problem)은 읽기 전용</b>: 비교용으로 세트의 Problem을 전량 로드하되 수정하지 않는다 → problem 테이블 UPDATE 0건.
 *   <li><b>행 단위 부분 업데이트(skip-clean)</b>: 중복 M건의 품질 로그만 마킹 → flush 시 M건만 UPDATE, 나머지 로드분 스킵.
 *   <li><b>컬럼 단위 부분 업데이트(@DynamicUpdate)</b>: 각 UPDATE의 SET 절은 review뿐.
 * </ul>
 */
@TestPropertySource(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.icc.qasker.quizset.support.SqlCapture")
class SetDiversityReviewScenarioTest extends JpaIntegrationTestBase {

  @Autowired private ProblemRepository problemRepository;
  @Autowired private ProblemQualityLogRepository qualityLogRepository;

  @Test
  @DisplayName("Problem 내용(title) 교차 비교로 중복 2건만 품질 로그 마킹, serving·나머지는 무접촉")
  void marksDuplicatesByComparingActualProblemContent() {
    ProblemSet set = persistSet();
    long setId = set.getId();

    // 1·2·5번은 같은 문항 내용(공백 변형 포함), 3·4번은 유일
    persistProblem(set, 1, "정규화의 목적은 무엇인가");
    persistProblem(set, 2, "정규화의 목적은 무엇인가");
    persistProblem(set, 3, "인덱스는 왜 필요한가");
    persistProblem(set, 4, "트랜잭션 격리수준이란");
    persistProblem(set, 5, "정규화의  목적은   무엇인가 ");
    for (int number = 1; number <= 5; number++) {
      persistQuality(setId, number);
    }
    flushAndClear();

    statistics().clear();
    SqlCapture.clear();

    // 전량 로드: 비교용 Problem(서빙) + 갱신 대상 품질 로그
    List<Problem> problems = problemRepository.findByIdProblemSetId(setId);
    problems.sort(Comparator.comparingInt(p -> p.getId().getNumber()));
    Map<Integer, ProblemQualityLog> qualityByNumber = new HashMap<>();
    for (ProblemQualityLog q : qualityLogRepository.findByProblemSetIdIn(List.of(setId))) {
      qualityByNumber.put(q.getNumber(), q);
    }
    assertThat(problems).hasSize(5);
    assertThat(qualityByNumber).hasSize(5);

    // 실제 문항 내용(title) 교차 비교 → 대표(최소 번호) 유지, 중복은 품질 로그만 마킹
    Map<String, Integer> representativeByContent = new HashMap<>();
    for (Problem problem : problems) {
      int number = problem.getId().getNumber();
      String contentKey = normalize(problem.getTitle());
      Integer representative = representativeByContent.putIfAbsent(contentKey, number);
      if (representative != null) {
        qualityByNumber
            .get(number)
            .applyReview(null, number + "번은 " + representative + "번과 문항 내용 중복");
      }
    }
    em.flush();

    // (A) 서빙 Problem은 읽기 전용 — problem 테이블 UPDATE 0건
    assertThat(SqlCapture.updatesFor("problem")).as("serving 무접촉").isEmpty();

    // (B) 행 단위: 로드 5건 중 중복 2건(2·5번)만 품질 로그 UPDATE
    assertThat(statistics().getEntityUpdateCount())
        .as("skip-clean: 로드 N=5 중 중복 M=2만 UPDATE")
        .isEqualTo(2);

    // (C) 컬럼 단위: SET 절에 바뀐 컬럼(review)만, 미변경 대형 컬럼(v1_explanation)은 미포함
    List<String> updates = SqlCapture.updatesFor("problem_quality_log");
    assertThat(updates).hasSize(2);
    assertThat(updates)
        .allSatisfy(
            sql -> {
              assertThat(sql).contains("review");
              assertThat(sql).doesNotContain("v1_explanation");
            });

    // 결과: 대표(1)는 무접촉(review=null), 중복(2)은 review 마킹
    flushAndClear();
    assertThat(quality(setId, 1).getReview()).isNull();
    ProblemQualityLog dup = quality(setId, 2);
    assertThat(dup.getReview()).isEqualTo("2번은 1번과 문항 내용 중복");
  }

  private static String normalize(String title) {
    return title.replaceAll("\\s+", "");
  }

  private ProblemQualityLog quality(long setId, int number) {
    return qualityLogRepository.findByProblemSetIdAndNumber(setId, number).orElseThrow();
  }

  private ProblemSet persistSet() {
    ProblemSet set =
        ProblemSet.builder()
            .sessionId("sess-diversity")
            .title("세트")
            .userId("user-1")
            .totalQuizCount(5)
            .quizType(QuizType.MULTIPLE)
            .fileUrl("file-url")
            .build();
    em.persist(set);
    return set;
  }

  private void persistProblem(ProblemSet set, int number, String title) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().number(number).build())
            .title(title)
            .problemSet(set)
            .build();
    problem.bindQuizData(List.of(new Selection("보기", null, true)), List.of(1));
    em.persist(problem);
  }

  private void persistQuality(long setId, int number) {
    em.persist(
        ProblemQualityLog.builder()
            .problemSetId(setId)
            .number(number)
            .v1Explanation("해설 " + number)
            .build());
  }
}
