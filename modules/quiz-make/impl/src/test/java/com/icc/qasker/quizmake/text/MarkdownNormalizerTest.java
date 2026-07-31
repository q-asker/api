package com.icc.qasker.quizmake.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 마크다운 정규화기 단위 테스트 — 계약 §7.3-2 경계(글루된 표 복원 / no-op 회귀 / 무손실)를 고정한다. */
class MarkdownNormalizerTest {

  @Test
  @DisplayName("글루된 표: 헤더/구분행/본문이 한 줄에 몰린 표를 개행 분리된 GFM 표로 복원한다")
  void repairs_glued_table() {
    String glued = "| 소기관 | 위치 | | :--- | :--- | | 미토콘드리아 | 동물 | | 엽록체 | 식물 |";

    String result = MarkdownNormalizer.normalize(glued);

    assertThat(result)
        .isEqualTo("| 소기관 | 위치 |\n" + "| :--- | :--- |\n" + "| 미토콘드리아 | 동물 |\n" + "| 엽록체 | 식물 |");
  }

  @Test
  @DisplayName("글루된 표 앞의 산문 접두 텍스트는 표 위 문단으로 분리된다")
  void repairs_glued_table_with_prose_prefix() {
    String glued = "다음 표를 보자. | A | B | | --- | --- | | 1 | 2 |";

    String result = MarkdownNormalizer.normalize(glued);

    assertThat(result).isEqualTo("다음 표를 보자.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |");
  }

  @Test
  @DisplayName("정상(개행 분리) 표는 변형하지 않는다")
  void keeps_wellformed_table_unchanged() {
    String table = "| A | B |\n| :--- | :--- |\n| 1 | 2 |\n| 3 | 4 |";

    assertThat(MarkdownNormalizer.normalize(table)).isEqualTo(table);
  }

  @Test
  @DisplayName("멱등: 복원 결과를 다시 정규화해도 동일하다")
  void is_idempotent() {
    String glued = "| A | B | | --- | --- | | 1 | 2 |";

    String once = MarkdownNormalizer.normalize(glued);
    String twice = MarkdownNormalizer.normalize(once);

    assertThat(twice).isEqualTo(once);
  }

  @Test
  @DisplayName("산문 속 리터럴 파이프(P(A|B), |x|)는 구분행이 없으므로 건드리지 않는다")
  void leaves_literal_pipes_in_prose_untouched() {
    String prose = "조건부확률 P(A|B)와 절댓값 |x|는 표가 아니다.";

    assertThat(MarkdownNormalizer.normalize(prose)).isEqualTo(prose);
  }

  @Test
  @DisplayName("구분행이 소실된 파이프 나열은 복원 대상이 아니다(원문 유지, 무손실)")
  void does_not_touch_pipe_soup_without_separator() {
    String soup = "| a | b | | c | d | | e | f |";

    assertThat(MarkdownNormalizer.normalize(soup)).isEqualTo(soup);
  }

  @Test
  @DisplayName("서식 없는 일반 텍스트는 완전 no-op이다(FR-006 회귀 0)")
  void plain_text_is_noop() {
    String plain = "이 문제는 특별한 서식이 없는 일반 지문이다. 파이프도 없다.";

    assertThat(MarkdownNormalizer.normalize(plain)).isEqualTo(plain);
  }

  @Test
  @DisplayName("null·빈 문자열은 그대로 반환한다")
  void null_and_blank_passthrough() {
    assertThat(MarkdownNormalizer.normalize(null)).isNull();
    assertThat(MarkdownNormalizer.normalize("")).isEqualTo("");
    assertThat(MarkdownNormalizer.normalize("   ")).isEqualTo("   ");
  }

  @Test
  @DisplayName("헤더 셀 수가 열 수와 불일치하면 모호하므로 복원하지 않는다(무손실)")
  void bails_when_header_count_mismatches_columns() {
    // 구분행은 2열인데 앞에 셀이 3개 → 헤더 경계 모호 → 원문 유지
    String ambiguous = "| x | y | z | | --- | --- | | 1 | 2 |";

    assertThat(MarkdownNormalizer.normalize(ambiguous)).isEqualTo(ambiguous);
  }

  @Test
  @DisplayName("본문이 여러 줄 개행으로 이어진 표에서 구분행만 정상이면 전체가 no-op이다")
  void multiline_table_all_lines_noop() {
    String table =
        "설명 문단입니다.\n\n"
            + "| 항목 | 값 |\n"
            + "| --- | --- |\n"
            + "| 가 | 1 |\n"
            + "| 나 | 2 |\n\n"
            + "이어지는 설명.";

    assertThat(MarkdownNormalizer.normalize(table)).isEqualTo(table);
  }

  @Test
  @DisplayName("불완전한 마지막 본문 행(셀 부족)도 내용을 버리지 않고 복원한다")
  void preserves_incomplete_trailing_row() {
    String glued = "| A | B | | --- | --- | | 1 | 2 | | 3 |";

    String result = MarkdownNormalizer.normalize(glued);

    assertThat(result).isEqualTo("| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 |");
    assertThat(result).contains("3"); // 마지막 잔여 셀 유실 없음
  }
}
