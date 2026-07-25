package com.icc.qasker.quizset.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.TestEntityFactory;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ProblemMapper의 REAL_BLANK 인정 답 sanitize 배선 검증 (contract.md §7.2/§7.4). */
class ProblemMapperTest {

  private final ProblemMapper mapper = TestEntityFactory.problemMapper();

  private static SelectionsOfAI aiSelection(
      String content, boolean correct, List<List<String>> acceptedAnswers) {
    SelectionsOfAI s = new SelectionsOfAI();
    s.setContent(content);
    s.setExplanation("해설");
    s.setCorrect(correct);
    s.setAcceptedAnswers(acceptedAnswers);
    return s;
  }

  private static QuizGeneratedFromAI quiz(List<SelectionsOfAI> selections) {
    QuizGeneratedFromAI q = new QuizGeneratedFromAI();
    q.setNumber(1);
    q.setTitle("문항");
    q.setSelections(selections);
    q.setReferencedPages(List.of(1));
    return q;
  }

  private static Selection correctOf(Problem problem) {
    return problem.getSelections().stream().filter(Selection::correct).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("REAL_BLANK: 정답 선지에만 sanitize된 인정 답이 실리고, 함정과 겹치는 후보는 제거된다")
  void realBlankSanitizesCorrectSelection() {
    ProblemSet set =
        TestEntityFactory.problemSet(1L, "s", "t", null, QuizType.REAL_BLANK, 1, "u", List.of());
    QuizGeneratedFromAI quiz =
        quiz(
            List.of(
                aiSelection("능동수송", true, List.of(List.of("active transport", "수동 수송"))),
                aiSelection("수동수송", false, null), // 함정
                aiSelection("촉진확산", false, null))); // 함정

    Problem problem = mapper.fromResponse(quiz, set);

    // 정답 선지: "수동 수송"은 함정 "수동수송"과 정규화 후 충돌 → 제거, "active transport"만 유지.
    assertThat(correctOf(problem).acceptedAnswers()).containsExactly(List.of("active transport"));
    // 함정(오답) 선지: 항상 null.
    problem.getSelections().stream()
        .filter(s -> !s.correct())
        .forEach(s -> assertThat(s.acceptedAnswers()).isNull());
  }

  @Test
  @DisplayName("REAL_BLANK 다중 빈칸: 빈칸 위치별로 정렬 저장된다")
  void realBlankMultiBlank() {
    ProblemSet set =
        TestEntityFactory.problemSet(1L, "s", "t", null, QuizType.REAL_BLANK, 1, "u", List.of());
    QuizGeneratedFromAI quiz =
        quiz(
            List.of(
                aiSelection("감수분열, 체세포분열", true, List.of(List.of("meiosis"), List.of("mitosis"))),
                aiSelection("체세포분열, 감수분열", false, null)));

    Problem problem = mapper.fromResponse(quiz, set);

    assertThat(correctOf(problem).acceptedAnswers())
        .containsExactly(List.of("meiosis"), List.of("mitosis"));
  }

  @Test
  @DisplayName("비 REAL_BLANK: AI가 인정 답을 줘도 모든 선지 acceptedAnswers는 null(기능 미적용)")
  void nonRealBlankIgnoresAcceptedAnswers() {
    ProblemSet set =
        TestEntityFactory.problemSet(1L, "s", "t", null, QuizType.MULTIPLE, 1, "u", List.of());
    QuizGeneratedFromAI quiz =
        quiz(
            List.of(
                aiSelection("정답", true, List.of(List.of("무시됨"))), aiSelection("오답", false, null)));

    Problem problem = mapper.fromResponse(quiz, set);

    assertThat(problem.getSelections()).allSatisfy(s -> assertThat(s.acceptedAnswers()).isNull());
  }
}
