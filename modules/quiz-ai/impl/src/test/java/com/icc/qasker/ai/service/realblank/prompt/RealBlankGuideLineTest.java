package com.icc.qasker.ai.service.realblank.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** REAL_BLANK 가이드라인이 오답 선택지 생성 지시 없이, 허용답안 지시는 갖추고 있는지 검증한다(FR-002·FR-003 회귀 방지). */
class RealBlankGuideLineTest {

  @Test
  @DisplayName("REAL_BLANK 가이드라인엔 오답 생성 지시가 없다")
  void does_not_instruct_distractors() {
    // "오답 선택지를 만들지 않는다"는 금지 규칙 자체는 남아있어야 하므로, 오답 "구성" 지시의 마커만 검사한다.
    assertThat(RealBlankGuideLine.content).doesNotContain("혼동유발형");
    assertThat(RealBlankGuideLine.content).doesNotContain("유사개념형");
    assertThat(RealBlankGuideLine.content).doesNotContain("오답:");
    assertThat(RealBlankGuideLine.content).doesNotContain("오답은");
    assertThat(RealBlankGuideLine.content).doesNotContain("오답 선택지 해설");
  }

  @Test
  @DisplayName("REAL_BLANK 가이드라인은 오답 선택지를 만들지 말라는 금지 규칙을 명시한다")
  void explicitly_forbids_distractors() {
    assertThat(RealBlankGuideLine.content).contains("오답 선택지를 만들지 않는다");
  }

  @Test
  @DisplayName("REAL_BLANK 가이드라인은 허용답안 생성 지시를 포함한다")
  void instructs_accepted_answers() {
    assertThat(RealBlankGuideLine.content).contains("acceptedAnswers");
    assertThat(RealBlankGuideLine.content).contains("허용변형");
  }
}
