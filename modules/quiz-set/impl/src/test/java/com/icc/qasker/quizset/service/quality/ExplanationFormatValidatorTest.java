package com.icc.qasker.quizset.service.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 해설 형식 정규식 검증 규칙(R1~R6)을 단위 검증한다. */
class ExplanationFormatValidatorTest {

  private final ExplanationFormatValidator validator = new ExplanationFormatValidator();

  private static final String VALID =
      """
      - **평가 수준**: 적용

      ---

      ## 정답 선택지

      > 정답 선지 내용

      이 선지가 정답인 이유를 충분히 설명하는 해설 본문입니다. 최소 분량 요건을 넘기기 위해 개념과 근거를 상세히 서술하고 추가 문장으로 길이를 확보합니다.

      ---

      ## 오답 선택지

      > 오답 선지 내용

      이 선지가 오답인 이유를 설명합니다.
      """;

  @Test
  @DisplayName("정형 구조를 모두 갖춘 해설은 통과한다")
  void passesWellFormed() {
    ExplanationFormatValidator.Result result = validator.validate(VALID);
    assertThat(result.passed()).isTrue();
    assertThat(result.summary()).isNull();
  }

  @Test
  @DisplayName("null·공백 해설은 미달이다")
  void failsBlank() {
    assertThat(validator.validate(null).passed()).isFalse();
    assertThat(validator.validate("   ").passed()).isFalse();
  }

  @Test
  @DisplayName("정답 헤더가 없으면 미달이고 요약에 사유가 담긴다")
  void failsMissingCorrectHeader() {
    ExplanationFormatValidator.Result result =
        validator.validate(VALID.replace("## 정답 선택지", "## 기타"));
    assertThat(result.passed()).isFalse();
    assertThat(result.summary()).contains("정답 선택지 헤더");
  }

  @Test
  @DisplayName("최소 분량 미달이면 미달이다")
  void failsTooShort() {
    ExplanationFormatValidator.Result result = validator.validate("## 정답 선택지\n> a\n## 오답 선택지\n> b");
    assertThat(result.passed()).isFalse();
    assertThat(result.summary()).contains("최소 분량");
  }

  @Test
  @DisplayName("평가 수준 태그 누락은 권고 위반이라 통과하되 요약에 남는다")
  void advisoryOnlyStillPasses() {
    ExplanationFormatValidator.Result result =
        validator.validate(VALID.replace("- **평가 수준**: 적용", "일반 문장입니다"));
    assertThat(result.passed()).isTrue();
    assertThat(result.summary()).contains("평가 수준 태그");
  }
}
