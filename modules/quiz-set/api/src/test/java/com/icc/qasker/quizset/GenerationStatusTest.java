package com.icc.qasker.quizset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GenerationStatusTest {

  @Test
  @DisplayName("toClientVisible: PROBLEMS_READY(내부 전용)는 COMPLETED로 번역된다")
  void problems_ready_translates_to_completed() {
    assertThat(GenerationStatus.PROBLEMS_READY.toClientVisible())
        .isEqualTo(GenerationStatus.COMPLETED);
  }

  @ParameterizedTest
  @EnumSource(
      value = GenerationStatus.class,
      names = {"FAILED", "GENERATING", "COMPLETED"})
  @DisplayName("toClientVisible: PROBLEMS_READY 외 상태는 그대로 노출된다")
  void other_statuses_are_identity(GenerationStatus status) {
    assertThat(status.toClientVisible()).isEqualTo(status);
  }
}
