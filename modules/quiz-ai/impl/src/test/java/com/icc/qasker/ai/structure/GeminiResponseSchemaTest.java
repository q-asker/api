package com.icc.qasker.ai.structure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 유형별 응답 스키마에 acceptedAnswers 필드가 조건부로만 노출되는지 검증한다(FR-002 회귀 방지). */
class GeminiResponseSchemaTest {

  @Test
  @DisplayName("acceptedAnswers 미포함 요청엔 스키마에 acceptedAnswers 프로퍼티가 없다")
  void excludes_accepted_answers_when_not_requested() {
    String schema = GeminiResponseSchema.forInstruction(null, false);
    assertThat(schema).doesNotContain("acceptedAnswers");
  }

  @Test
  @DisplayName("acceptedAnswers 포함 요청(REAL_BLANK)엔 스키마에 acceptedAnswers 프로퍼티가 있다")
  void includes_accepted_answers_when_requested() {
    String schema = GeminiResponseSchema.forInstruction(null, true);
    assertThat(schema).contains("acceptedAnswers");
  }

  @Test
  @DisplayName("customInstruction이 있어도 acceptedAnswers 노출 여부는 독립적으로 결정된다")
  void accepted_answers_independent_of_custom_instruction() {
    String withInstructionNoAccepted = GeminiResponseSchema.forInstruction("사용자 지시", false);
    assertThat(withInstructionNoAccepted).contains("appliedInstruction");
    assertThat(withInstructionNoAccepted).doesNotContain("acceptedAnswers");

    String withInstructionAndAccepted = GeminiResponseSchema.forInstruction("사용자 지시", true);
    assertThat(withInstructionAndAccepted).contains("appliedInstruction");
    assertThat(withInstructionAndAccepted).contains("acceptedAnswers");
  }
}
