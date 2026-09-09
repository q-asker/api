package com.icc.qasker.quizset.service.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.ProblemSetOrigin;
import com.icc.qasker.quizset.dto.WrongAnswerSetCreation;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** 재출제 동일성(FR-004)과 혈통 승계를 실제 저장 결과로 고정한다. */
class WrongAnswerSetCreationServiceImplTest extends JpaIntegrationTestBase {

  @Autowired private ProblemSetRepository problemSetRepository;
  @Autowired private ProblemRepository problemRepository;

  private WrongAnswerSetCreationServiceImpl service() {
    return new WrongAnswerSetCreationServiceImpl(problemSetRepository, problemRepository);
  }

  private ProblemSet persistOriginalSet(String sessionId) {
    ProblemSet set =
        ProblemSet.builder()
            .sessionId(sessionId)
            .title("원본")
            .userId("me")
            .quizType(QuizType.MULTIPLE)
            .totalQuizCount(1)
            .fileUrl("https://cdn/original.pdf")
            .generationStatus(GenerationStatus.COMPLETED)
            .build();
    em.persist(set);
    return set;
  }

  private Problem persistProblem(ProblemSet set, int number) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().problemSetId(set.getId()).number(number).build())
            .problemSet(set)
            .title("지문 " + number)
            .build();
    problem.bindQuizData(
        List.of(
            new Selection("보기1", "선지해설1", false, null),
            new Selection("정답", "선지해설2", true, List.of(List.of("정답", "answer")))),
        List.of(3, 4));
    problem.updateExplanation("해설 " + number);
    problem.updateAppliedInstruction("지시 " + number);
    em.persist(problem);
    return problem;
  }

  private WrongAnswerSetCreation creation(String sessionId, List<ProblemLineage> sources) {
    return new WrongAnswerSetCreation("me", sessionId, "오답 모음", QuizType.MULTIPLE, 5L, sources);
  }

  @Test
  @DisplayName("문항의 지문·선택지·정답·해설을 그대로 복제하고 번호만 1부터 다시 매긴다")
  void clones_problem_content() {
    ProblemSet origin = persistOriginalSet("origin");
    persistProblem(origin, 4);
    persistProblem(origin, 9);
    flushAndClear();

    Long createdId =
        service()
            .create(
                creation(
                    "wa-key-MULTIPLE",
                    List.of(
                        new ProblemLineage(origin.getId(), 9),
                        new ProblemLineage(origin.getId(), 4))));
    flushAndClear();

    List<Problem> clones = problemRepository.findExplanationsBySetId(createdId);
    assertThat(clones).extracting(p -> p.getId().getNumber()).containsExactly(1, 2);
    // 요청한 순서(9번 먼저)가 새 번호 순서가 된다.
    assertThat(clones.getFirst().getTitle()).isEqualTo("지문 9");
    assertThat(clones.getFirst().getExplanationContent()).isEqualTo("해설 9");
    assertThat(clones.getFirst().getAppliedInstruction()).isEqualTo("지시 9");
    assertThat(clones.getFirst().getSelections())
        .containsExactlyElementsOf(
            List.of(
                new Selection("보기1", "선지해설1", false, null),
                new Selection("정답", "선지해설2", true, List.of(List.of("정답", "answer")))));
  }

  @Test
  @DisplayName("원본 자료를 가리키는 페이지 번호는 물려주지 않는다")
  void drops_referenced_pages() {
    ProblemSet origin = persistOriginalSet("origin");
    persistProblem(origin, 1);
    flushAndClear();

    Long createdId =
        service()
            .create(creation("wa-key-MULTIPLE", List.of(new ProblemLineage(origin.getId(), 1))));
    flushAndClear();

    assertThat(problemRepository.findExplanationsBySetId(createdId).getFirst().getReferencedPages())
        .isEmpty();
  }

  @Test
  @DisplayName("만들어진 세트는 자료 없이도 바로 풀 수 있는 상태이고 출처가 오답 모음으로 남는다")
  void created_set_is_ready_to_solve() {
    ProblemSet origin = persistOriginalSet("origin");
    persistProblem(origin, 1);
    flushAndClear();

    Long createdId =
        service()
            .create(creation("wa-key-MULTIPLE", List.of(new ProblemLineage(origin.getId(), 1))));
    flushAndClear();

    ProblemSet created = problemSetRepository.findById(createdId).orElseThrow();
    assertThat(created.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
    assertThat(created.getOrigin()).isEqualTo(ProblemSetOrigin.WRONG_ANSWER);
    assertThat(created.getSourceFolderId()).isEqualTo(5L);
    assertThat(created.getFileUrl()).isEmpty();
    assertThat(created.getTotalQuizCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("세대가 거듭돼도 혈통은 최초 조상을 가리킨다")
  void lineage_points_to_first_ancestor() {
    ProblemSet origin = persistOriginalSet("origin");
    persistProblem(origin, 7);
    flushAndClear();

    Long firstGeneration =
        service().create(creation("wa-1-MULTIPLE", List.of(new ProblemLineage(origin.getId(), 7))));
    flushAndClear();

    Long secondGeneration =
        service()
            .create(creation("wa-2-MULTIPLE", List.of(new ProblemLineage(firstGeneration, 1))));
    flushAndClear();

    Problem second = problemRepository.findExplanationsBySetId(secondGeneration).getFirst();
    assertThat(second.getOriginProblemSetId()).isEqualTo(origin.getId());
    assertThat(second.getOriginNumber()).isEqualTo(7);
  }

  @Test
  @DisplayName("같은 세션 식별자로 두 번 만들면 두 번째는 제약에 걸린다 — 호출자가 이걸 보고 기존 것을 돌려준다")
  void duplicate_session_is_rejected() {
    ProblemSet origin = persistOriginalSet("origin");
    persistProblem(origin, 1);
    flushAndClear();

    service().create(creation("wa-same-MULTIPLE", List.of(new ProblemLineage(origin.getId(), 1))));
    flushAndClear();

    assertThatThrownBy(
            () -> {
              service()
                  .create(
                      creation("wa-same-MULTIPLE", List.of(new ProblemLineage(origin.getId(), 1))));
              em.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
