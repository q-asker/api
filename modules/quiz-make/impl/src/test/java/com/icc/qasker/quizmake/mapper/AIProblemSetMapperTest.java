package com.icc.qasker.quizmake.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 저장 초크포인트에서 글루된 표 마크다운이 지문·선지·해설 전부에 대해 정규화되는지 검증한다(계약 §7.3-2). */
class AIProblemSetMapperTest {

  @Test
  @DisplayName("toQuiz: 지문·선지·해설의 글루된 표가 개행 분리된 GFM 표로 정규화된다")
  void normalizes_glued_tables_across_fields() {
    AIProblem problem =
        new AIProblem(
            "본문 | A | B | | --- | --- | | 1 | 2 |",
            "Apply",
            List.of(
                new AISelection(
                    "선지 | X | Y | | --- | --- | | 9 | 8 |",
                    "해설 | P | Q | | --- | --- | | 5 | 6 |",
                    true)),
            List.of(1),
            null);

    QuizGeneratedFromAI quiz = AIProblemSetMapper.toQuiz(problem);

    assertThat(quiz.getTitle()).isEqualTo("본문\n\n| A | B |\n| --- | --- |\n| 1 | 2 |");
    assertThat(quiz.getSelections().get(0).getContent())
        .isEqualTo("선지\n\n| X | Y |\n| --- | --- |\n| 9 | 8 |");
    assertThat(quiz.getSelections().get(0).getExplanation())
        .isEqualTo("해설\n\n| P | Q |\n| --- | --- |\n| 5 | 6 |");
  }

  @Test
  @DisplayName("toQuiz: 서식 없는 일반 지문은 그대로 유지된다(회귀 0)")
  void keeps_plain_content_unchanged() {
    AIProblem problem =
        new AIProblem(
            "다음 중 옳은 것은 무엇인가?",
            "Apply",
            List.of(new AISelection("보기 1", "해설 1", true)),
            List.of(1),
            null);

    QuizGeneratedFromAI quiz = AIProblemSetMapper.toQuiz(problem);

    assertThat(quiz.getTitle()).isEqualTo("다음 중 옳은 것은 무엇인가?");
    assertThat(quiz.getSelections().get(0).getContent()).isEqualTo("보기 1");
    assertThat(quiz.getSelections().get(0).getExplanation()).isEqualTo("해설 1");
  }
}
