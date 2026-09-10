package com.icc.qasker.quizmake.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 저장 직전 선지 순서 확정(SelectionArrangement) 단위 테스트 — 셔플/OX 정규화 회귀 방지. */
class SelectionArrangementTest {

  private static SelectionsOfAI selection(String content, boolean correct) {
    SelectionsOfAI sel = new SelectionsOfAI();
    sel.setContent(content);
    sel.setCorrect(correct);
    return sel;
  }

  private static QuizGeneratedFromAI quizWith(List<SelectionsOfAI> selections) {
    QuizGeneratedFromAI quiz = new QuizGeneratedFromAI();
    quiz.setSelections(selections);
    return quiz;
  }

  @Test
  @DisplayName("MULTIPLE: 선지 집합은 보존된다(순서만 무작위화)")
  void shuffle_preserves_set() {
    QuizGeneratedFromAI quiz =
        quizWith(
            List.of(
                selection("A", true),
                selection("B", false),
                selection("C", false),
                selection("D", false)));

    SelectionArrangement.arrange(quiz, QuizType.MULTIPLE);

    assertThat(quiz.getSelections())
        .extracting(SelectionsOfAI::getContent)
        .containsExactlyInAnyOrder("A", "B", "C", "D");
    assertThat(quiz.getSelections())
        .filteredOn(SelectionsOfAI::isCorrect)
        .extracting(SelectionsOfAI::getContent)
        .containsExactly("A");
  }

  @Test
  @DisplayName("OX: X가 1번이면 O가 1번이 되도록 순서를 바꾼다")
  void normalize_ox_moves_o_first() {
    QuizGeneratedFromAI quiz = quizWith(List.of(selection("X", false), selection("O", true)));

    SelectionArrangement.arrange(quiz, QuizType.OX);

    assertThat(quiz.getSelections().get(0).getContent()).isEqualTo("O");
    assertThat(quiz.getSelections().get(0).isCorrect()).isTrue();
  }

  @Test
  @DisplayName("OX: O가 이미 1번이면 순서를 유지한다")
  void normalize_ox_keeps_order_when_o_first() {
    QuizGeneratedFromAI quiz = quizWith(List.of(selection("O", true), selection("X", false)));

    SelectionArrangement.arrange(quiz, QuizType.OX);

    assertThat(quiz.getSelections().get(0).getContent()).isEqualTo("O");
  }

  @Test
  @DisplayName("ESSAY·REAL_BLANK: 선지는 정답 보존용이므로 순서를 건드리지 않는다")
  void leaves_answer_holding_types_untouched() {
    for (QuizType type : List.of(QuizType.ESSAY, QuizType.REAL_BLANK)) {
      QuizGeneratedFromAI quiz = quizWith(List.of(selection("모범답안", true)));

      SelectionArrangement.arrange(quiz, type);

      assertThat(quiz.getSelections())
          .extracting(SelectionsOfAI::getContent)
          .containsExactly("모범답안");
    }
  }
}
