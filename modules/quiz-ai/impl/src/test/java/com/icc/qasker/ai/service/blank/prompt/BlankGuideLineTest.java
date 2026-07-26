package com.icc.qasker.ai.service.blank.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BLANK 가이드라인이 허용답안(REAL_BLANK 전용 관심사)에 오염되지 않는지 검증한다(FR-002 회귀 방지). */
class BlankGuideLineTest {

  @Test
  @DisplayName("BLANK 가이드라인엔 허용답안 생성 지시가 없다")
  void does_not_instruct_accepted_answers() {
    assertThat(BlankGuideLine.content).doesNotContain("acceptedAnswers");
    assertThat(BlankGuideLine.content).doesNotContain("허용답안");
    assertThat(BlankGuideLine.content).doesNotContain("허용변형");
  }

  @Test
  @DisplayName("BLANK 가이드라인은 여전히 오답 지시를 포함한다(선지형 유지 회귀 방지)")
  void still_instructs_distractors() {
    assertThat(BlankGuideLine.content).contains("오답");
  }
}
