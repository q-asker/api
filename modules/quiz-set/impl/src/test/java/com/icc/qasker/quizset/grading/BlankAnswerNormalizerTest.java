package com.icc.qasker.quizset.grading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** contract.md §7.3 정규화 파이프라인 검증. 프론트 blank-scoring.ts와 문자 단위 동일해야 한다. */
class BlankAnswerNormalizerTest {

  @Test
  @DisplayName("null·빈 입력은 빈 문자열로 정규화된다")
  void nullAndBlank() {
    assertThat(BlankAnswerNormalizer.normalize(null)).isEmpty();
    assertThat(BlankAnswerNormalizer.normalize("")).isEmpty();
    assertThat(BlankAnswerNormalizer.normalize("   ")).isEmpty();
  }

  @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
  @DisplayName("표기 노이즈(공백·대소문자·문장부호·전각반각)는 흡수한다")
  @CsvSource({
    "'Event Loop', eventloop",
    "'event loop', eventloop",
    "'이벤트 루프', 이벤트루프",
    "'TCP/IP', tcpip",
    "'A.P.I.', api",
    "'  trimmed  ', trimmed",
    "'３．１４', 314", // 전각 숫자·마침표 → NFKC 반각 후 마침표(\p{P}) 제거
    "'ＡＢＣ', abc" // 전각 영문 → 반각 소문자
  })
  void normalizesNoise(String input, String expected) {
    assertThat(BlankAnswerNormalizer.normalize(input)).isEqualTo(expected);
  }

  @Test
  @DisplayName("심볼(\\p{S})은 보존한다 — C++↔C, C#↔C 구분 유지(SC-002)")
  void preservesSymbols() {
    assertThat(BlankAnswerNormalizer.normalize("C++")).isEqualTo("c++");
    assertThat(BlankAnswerNormalizer.normalize("C#")).isEqualTo("c#");
    assertThat(BlankAnswerNormalizer.normalize("C")).isEqualTo("c");
    assertThat(BlankAnswerNormalizer.normalize("SYN+ACK")).isEqualTo("syn+ack");
    // 심볼 보존으로 C++ 와 C 는 정규화 후에도 다르다.
    assertThat(BlankAnswerNormalizer.normalize("C++"))
        .isNotEqualTo(BlankAnswerNormalizer.normalize("C"));
  }

  @Test
  @DisplayName("소수점(\\p{P})은 제거되어 3.14=314로 관용된다")
  void decimalPointRemoved() {
    assertThat(BlankAnswerNormalizer.normalize("3.14")).isEqualTo("314");
  }
}
