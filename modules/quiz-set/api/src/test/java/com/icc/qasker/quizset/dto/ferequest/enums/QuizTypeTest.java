package com.icc.qasker.quizset.dto.ferequest.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizTypeTest {

  @Test
  @DisplayName("REAL_BLANK는 전용 전략 이름 REAL_BLANK를 반환한다")
  void real_blank_uses_own_strategy() {
    assertThat(QuizType.REAL_BLANK.toAiStrategyName()).isEqualTo("REAL_BLANK");
  }

  @Test
  @DisplayName("BLANK는 자기 자신의 이름을 그대로 반환한다")
  void blank_keeps_own_name() {
    assertThat(QuizType.BLANK.toAiStrategyName()).isEqualTo("BLANK");
  }

  @Test
  @DisplayName("MULTIPLE은 자기 자신의 이름을 그대로 반환한다")
  void multiple_keeps_own_name() {
    assertThat(QuizType.MULTIPLE.toAiStrategyName()).isEqualTo("MULTIPLE");
  }

  @Test
  @DisplayName("OX는 자기 자신의 이름을 그대로 반환한다")
  void ox_keeps_own_name() {
    assertThat(QuizType.OX.toAiStrategyName()).isEqualTo("OX");
  }

  @Test
  @DisplayName("ESSAY는 자기 자신의 이름을 그대로 반환한다")
  void essay_keeps_own_name() {
    assertThat(QuizType.ESSAY.toAiStrategyName()).isEqualTo("ESSAY");
  }
}
