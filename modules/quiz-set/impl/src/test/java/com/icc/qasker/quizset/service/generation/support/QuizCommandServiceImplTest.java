package com.icc.qasker.quizset.service.generation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.icc.qasker.quizset.dto.airesponse.ExplanationGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.mapper.ProblemMapper;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.support.JpaIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({QuizCommandServiceImpl.class, ProblemMapper.class})
class QuizCommandServiceImplTest extends JpaIntegrationTestBase {

  @Autowired private QuizCommandServiceImpl quizCommandService;
  @Autowired private ProblemRepository problemRepository;

  @Test
  @DisplayName("Phase 2: saveExplanation은 해설만 채우고 문항 질문·선지 내용·정답·참조 페이지는 변경하지 않는다")
  void saveExplanations_fills_explanations_without_touching_problem_data() {
    Long problemSetId = initSetWithProblems();

    quizCommandService.saveExplanation(
        problemSetId, new ExplanationGeneratedFromAI(1, "문항 해설 마크다운", List.of("선지해설1", "선지해설2")));
    flushAndClear();

    Problem problem = findProblem(problemSetId, 1);
    assertThat(problem.getExplanationContent()).isEqualTo("문항 해설 마크다운");
    assertThat(problem.getTitle()).isEqualTo("문제1");
    assertThat(problem.getReferencedPages()).containsExactly(3, 14);
    assertThat(problem.getSelections())
        .extracting(Selection::content)
        .containsExactly("보기1", "보기2");
    assertThat(problem.getSelections())
        .extracting(Selection::explanation)
        .containsExactly("선지해설1", "선지해설2");
    assertThat(problem.getSelections()).extracting(Selection::correct).containsExactly(true, false);
  }

  @Test
  @DisplayName("Phase 2: 존재하지 않는 문항 번호는 건너뛰고 예외를 던지지 않는다")
  void saveExplanations_skips_unknown_numbers() {
    Long problemSetId = initSetWithProblems();

    assertThatCode(
            () ->
                quizCommandService.saveExplanation(
                    problemSetId, new ExplanationGeneratedFromAI(99, "유령 해설", List.of())))
        .doesNotThrowAnyException();
    flushAndClear();

    assertThat(findProblem(problemSetId, 1).getExplanationContent()).isNull();
  }

  @Test
  @DisplayName("Phase 2: 선지 해설 개수가 선지 수와 다르면 문항 해설만 저장하고 선지 해설은 갱신하지 않는다")
  void saveExplanations_keeps_selections_on_size_mismatch() {
    Long problemSetId = initSetWithProblems();

    quizCommandService.saveExplanation(
        problemSetId, new ExplanationGeneratedFromAI(1, "문항 해설", List.of("하나뿐")));
    flushAndClear();

    Problem problem = findProblem(problemSetId, 1);
    assertThat(problem.getExplanationContent()).isEqualTo("문항 해설");
    assertThat(problem.getSelections()).extracting(Selection::explanation).containsOnlyNulls();
  }

  /** Phase 1 형태(해설 없음)로 세트+문항을 저장하고 세트 id를 반환한다. */
  private Long initSetWithProblems() {
    Long problemSetId =
        quizCommandService.initProblemSet(
            "user-1", "session-1", "제목", 2, QuizType.MULTIPLE, "http://file", null);
    quizCommandService.saveBatch(List.of(quizWithoutExplanation()), problemSetId);
    flushAndClear();
    return problemSetId;
  }

  private QuizGeneratedFromAI quizWithoutExplanation() {
    SelectionsOfAI first = new SelectionsOfAI();
    first.setContent("보기1");
    first.setCorrect(true);
    SelectionsOfAI second = new SelectionsOfAI();
    second.setContent("보기2");
    second.setCorrect(false);

    QuizGeneratedFromAI quiz = new QuizGeneratedFromAI();
    quiz.setNumber(1);
    quiz.setTitle("문제1");
    quiz.setSelections(List.of(first, second));
    quiz.setReferencedPages(List.of(3, 14));
    return quiz;
  }

  private Problem findProblem(Long problemSetId, int number) {
    return problemRepository
        .findById(ProblemId.builder().problemSetId(problemSetId).number(number).build())
        .orElseThrow();
  }
}
