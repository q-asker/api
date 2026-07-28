package com.icc.qasker.quizset.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RealBlankGraderTest {

  @Nested
  @DisplayName("normalize — 표기 차이만 있는 답을 같은 문자열로 접는다 (FR-001)")
  class Normalize {

    @Test
    @DisplayName("공백·문장부호·대소문자·전각/반각 차이를 제거한다")
    void folds_notation_differences() {
      String base = RealBlankGrader.normalize("운동량");
      assertThat(RealBlankGrader.normalize(" 운동량 ")).isEqualTo(base);
      assertThat(RealBlankGrader.normalize("운동 량")).isEqualTo(base);
      assertThat(RealBlankGrader.normalize("운동량!")).isEqualTo(base);
    }

    @Test
    @DisplayName("영문 대소문자와 전각 영숫자를 접는다")
    void folds_case_and_fullwidth() {
      assertThat(RealBlankGrader.normalize("TCP")).isEqualTo(RealBlankGrader.normalize("tcp"));
      // 전각 영문 "ＴＣＰ" → NFKC → "TCP" → 소문자
      assertThat(RealBlankGrader.normalize("ＴＣＰ")).isEqualTo(RealBlankGrader.normalize("tcp"));
    }

    @Test
    @DisplayName("null·빈 입력은 빈 문자열로 정규화된다")
    void null_becomes_empty() {
      assertThat(RealBlankGrader.normalize(null)).isEmpty();
      assertThat(RealBlankGrader.normalize("   ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("isCorrect — 단일 빈칸")
  class SingleBlank {

    private final List<List<String>> accepted = List.of(List.of("미토콘드리아", "mitochondria"));

    @Test
    @DisplayName("표기 차이가 있어도 인정 집합에 있으면 정답 (FR-001)")
    void notation_variant_is_correct() {
      assertThat(RealBlankGrader.isCorrect(List.of(" 미토콘드리아 "), accepted)).isTrue();
    }

    @Test
    @DisplayName("동의어(영↔한 등)가 인정 집합에 있으면 정답 (FR-002)")
    void synonym_is_correct() {
      assertThat(RealBlankGrader.isCorrect(List.of("Mitochondria"), accepted)).isTrue();
    }

    @Test
    @DisplayName("인정 집합 밖(오탈자·인접 개념)은 오답 (FR-003·FR-005)")
    void typo_and_adjacent_are_wrong() {
      assertThat(RealBlankGrader.isCorrect(List.of("미토콘트리아"), accepted)).isFalse();
      assertThat(RealBlankGrader.isCorrect(List.of("엽록체"), accepted)).isFalse();
    }

    @Test
    @DisplayName("빈 답·공백만은 오답")
    void empty_is_wrong() {
      assertThat(RealBlankGrader.isCorrect(List.of(""), accepted)).isFalse();
      assertThat(RealBlankGrader.isCorrect(List.of("   "), accepted)).isFalse();
    }
  }

  @Nested
  @DisplayName("isCorrect — 다중 빈칸 (전 빈칸 AND)")
  class MultiBlank {

    private final List<List<String>> accepted =
        List.of(List.of("감수분열", "meiosis"), List.of("체세포분열", "mitosis"));

    @Test
    @DisplayName("모든 빈칸이 인정 집합에 있으면 정답")
    void all_blanks_correct() {
      assertThat(RealBlankGrader.isCorrect(List.of("감수분열", "체세포분열"), accepted)).isTrue();
      assertThat(RealBlankGrader.isCorrect(List.of("meiosis", "mitosis"), accepted)).isTrue();
    }

    @Test
    @DisplayName("한 빈칸이라도 틀리면 오답")
    void one_wrong_blank_fails() {
      assertThat(RealBlankGrader.isCorrect(List.of("감수분열", "무사분열"), accepted)).isFalse();
    }

    @Test
    @DisplayName("빈칸 순서가 바뀌면 오답 (위치 역전)")
    void swapped_order_fails() {
      assertThat(RealBlankGrader.isCorrect(List.of("체세포분열", "감수분열"), accepted)).isFalse();
    }

    @Test
    @DisplayName("조각 수가 빈칸 수와 다르면 오답")
    void arity_mismatch_fails() {
      assertThat(RealBlankGrader.isCorrect(List.of("감수분열"), accepted)).isFalse();
      assertThat(RealBlankGrader.isCorrect(List.of("감수분열", "체세포분열", "이분법"), accepted)).isFalse();
    }
  }

  @Nested
  @DisplayName("splitInputs — 직렬화된 입력 분해")
  class SplitInputs {

    @Test
    @DisplayName("단일 빈칸(구분자 없음)은 1조각")
    void single_blank() {
      assertThat(RealBlankGrader.splitInputs("운동량")).containsExactly("운동량");
    }

    @Test
    @DisplayName("U+001F 구분자로 다중 빈칸을 분해하고 trailing 빈 조각을 유지한다")
    void multi_blank_keeps_trailing_empty() {
      String d = RealBlankGrader.BLANK_DELIMITER;
      assertThat(RealBlankGrader.splitInputs("감수분열" + d + "체세포분열"))
          .containsExactly("감수분열", "체세포분열");
      assertThat(RealBlankGrader.splitInputs("감수분열" + d)).containsExactly("감수분열", "");
    }
  }

  @Nested
  @DisplayName("fallbackAccepted — 인정 집합 없는 구 문항 폴백 (FR-009)")
  class Fallback {

    @Test
    @DisplayName("단일 정답 content는 1빈칸 단일 인정값으로")
    void single() {
      List<List<String>> accepted = RealBlankGrader.fallbackAccepted("운동량");
      assertThat(RealBlankGrader.isCorrect(List.of("운동량"), accepted)).isTrue();
      assertThat(RealBlankGrader.isCorrect(List.of("운동에너지"), accepted)).isFalse();
    }

    @Test
    @DisplayName("콤마 결합 content는 빈칸별로 분해된다")
    void multi() {
      List<List<String>> accepted = RealBlankGrader.fallbackAccepted("감수분열, 체세포분열");
      assertThat(RealBlankGrader.isCorrect(List.of("감수분열", "체세포분열"), accepted)).isTrue();
    }

    @Test
    @DisplayName("표기 차이는 폴백에서도 정규화로 인정된다(현행 완전 일치의 상위 호환)")
    void fallback_still_normalizes() {
      List<List<String>> accepted = RealBlankGrader.fallbackAccepted("운동량");
      assertThat(RealBlankGrader.isCorrect(List.of(" 운동량 "), accepted)).isTrue();
    }
  }
}
