package com.icc.qasker.ai.structure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GeminiResponseSchema의 조건부 필드 노출 검증. acceptedAnswers는 REAL_BLANK(realBlank=true)에서만 스키마에 포함되어야 하며,
 * 그 외 타입 생성(MULTIPLE/OX/BLANK)에는 영향이 없어야 한다(회귀 방지).
 */
class GeminiResponseSchemaTest {

  @Test
  @DisplayName("비 REAL_BLANK 스키마에는 acceptedAnswers가 포함되지 않는다")
  void nonRealBlankExcludesAcceptedAnswers() {
    assertThat(GeminiResponseSchema.forInstruction(null, false)).doesNotContain("acceptedAnswers");
    assertThat(GeminiResponseSchema.forInstruction("사용자 지시", false))
        .doesNotContain("acceptedAnswers");
    // 1-arg 기본값도 제외.
    assertThat(GeminiResponseSchema.forInstruction(null)).doesNotContain("acceptedAnswers");
  }

  @Test
  @DisplayName("REAL_BLANK 스키마에는 acceptedAnswers가 포함된다")
  void realBlankIncludesAcceptedAnswers() {
    assertThat(GeminiResponseSchema.forInstruction(null, true)).contains("acceptedAnswers");
    assertThat(GeminiResponseSchema.forInstruction("사용자 지시", true)).contains("acceptedAnswers");
  }

  @Test
  @DisplayName("appliedInstruction은 customInstruction 유무로만 갈리고 acceptedAnswers와 독립적이다")
  void appliedInstructionIndependentOfAcceptedAnswers() {
    assertThat(GeminiResponseSchema.forInstruction(null, true))
        .doesNotContain("appliedInstruction");
    assertThat(GeminiResponseSchema.forInstruction("사용자 지시", true)).contains("appliedInstruction");
  }
}
