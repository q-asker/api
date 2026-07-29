package com.icc.qasker.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.service.prompt.MarkdownFormatGuideLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 출력 서식 규약이 전 타입의 시스템·문제 가이드라인에 SSOT로 prepend되는지 검증한다(계약 §7.3-1). */
class QuizTypeFormatGuideLineTest {

  @ParameterizedTest
  @EnumSource(QuizType.class)
  @DisplayName("모든 퀴즈 타입의 시스템 가이드라인이 출력 서식 규약으로 시작한다(KO)")
  void system_guideline_starts_with_format_contract_ko(QuizType type) {
    assertThat(type.getSystemGuideLine("KO")).startsWith(MarkdownFormatGuideLine.content);
  }

  @ParameterizedTest
  @EnumSource(QuizType.class)
  @DisplayName("모든 퀴즈 타입의 문제 가이드라인(검증기 재사용)도 서식 규약을 포함한다")
  void problem_guideline_contains_format_contract(QuizType type) {
    assertThat(type.getProblemGuideLine("KO")).startsWith(MarkdownFormatGuideLine.content);
  }

  @Test
  @DisplayName("서식 규약은 표 개행·리터럴 이스케이프 핵심 지시를 담는다")
  void format_contract_covers_key_rules() {
    String rule = MarkdownFormatGuideLine.content;
    assertThat(rule).contains("출력 서식 규약");
    assertThat(rule).contains("각각 새 줄"); // 표 행별 개행
    assertThat(rule).contains("\\$"); // 리터럴 $ 이스케이프
    assertThat(rule).contains("\\mid"); // 수식 세로막대
    assertThat(rule).contains("원시 HTML");
  }

  @Test
  @DisplayName("서식 규약은 해설 섹션 경계 마커를 포함하지 않는다(getProblemGuideLine split 불간섭)")
  void format_contract_does_not_contain_explanation_marker() {
    assertThat(MarkdownFormatGuideLine.content).doesNotContain("# Step 3 — 해설을 작성한다");
  }
}
