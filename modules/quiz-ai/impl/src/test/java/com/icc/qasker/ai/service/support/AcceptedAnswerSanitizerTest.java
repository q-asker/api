package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.dto.AISelection;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REAL_BLANK 허용변형 후처리 필터(AcceptedAnswerSanitizer) 단위 테스트. 핵심은 오답 보호(FR-005): 오답선지와 겹치는 변형이 저장 전에
 * 제거되어 허용변형 ∩ 오답선지 = ∅ 이 보장되는지 검증한다.
 */
class AcceptedAnswerSanitizerTest {

  private static AISelection correct(String content) {
    return new AISelection(content, null, true);
  }

  private static AISelection wrong(String content) {
    return new AISelection(content, null, false);
  }

  @Test
  @DisplayName("FR-005: 오답선지와 정규화 일치하는 변형은 제거된다")
  void removes_variants_matching_distractor() {
    List<AISelection> selections = List.of(correct("투사"), wrong("합리화"), wrong("억압"), wrong("전위"));
    List<List<String>> accepted = List.of(List.of("projection", "합리화", "투영"));

    List<List<String>> result = AcceptedAnswerSanitizer.sanitize(accepted, selections);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsExactly("projection", "투영").doesNotContain("합리화");
  }

  @Test
  @DisplayName("FR-005: 대소문자·공백만 다른 오답선지도 제거된다")
  void removes_variants_matching_distractor_case_insensitive() {
    List<AISelection> selections = List.of(correct("DB"), wrong("IP"), wrong("UDP"));
    List<List<String>> accepted = List.of(Arrays.asList("데이터베이스", " ip ", "database"));

    List<List<String>> result = AcceptedAnswerSanitizer.sanitize(accepted, selections);

    assertThat(result.get(0)).containsExactly("데이터베이스", "database");
  }

  @Test
  @DisplayName("다중 빈칸: 빈칸별 구조와 순서를 보존하며 각 빈칸에 오답선지 필터를 적용한다")
  void preserves_per_blank_structure() {
    // 정답 "감수분열, 체세포분열" / 위치역전 오답 "체세포분열, 감수분열"
    List<AISelection> selections = List.of(correct("감수분열, 체세포분열"), wrong("체세포분열, 감수분열"));
    List<List<String>> accepted = List.of(List.of("meiosis", "체세포분열"), List.of("mitosis", "감수분열"));

    List<List<String>> result = AcceptedAnswerSanitizer.sanitize(accepted, selections);

    assertThat(result).hasSize(2);
    // 오답선지 토큰(감수분열/체세포분열)은 위치 무관하게 제거된다(보수적)
    assertThat(result.get(0)).containsExactly("meiosis");
    assertThat(result.get(1)).containsExactly("mitosis");
  }

  @Test
  @DisplayName("빈 문자열·공백·중복 변형은 제거된다")
  void drops_blank_and_duplicate() {
    List<AISelection> selections = List.of(correct("동위원소"));
    List<List<String>> accepted = List.of(Arrays.asList("isotope", "", "  ", "isotope", "아이소토프"));

    List<List<String>> result = AcceptedAnswerSanitizer.sanitize(accepted, selections);

    assertThat(result.get(0)).containsExactly("isotope", "아이소토프");
  }

  @Test
  @DisplayName("허용변형이 null이면 null을 반환한다(허용변형 미생성 = 폴백 채점 대상)")
  void null_input_returns_null() {
    List<AISelection> selections = List.of(correct("투사"), wrong("합리화"));

    assertThat(AcceptedAnswerSanitizer.sanitize(null, selections)).isNull();
  }

  @Test
  @DisplayName("오답선지가 없으면 변형은 그대로 유지된다")
  void no_distractor_keeps_variants() {
    List<AISelection> selections = List.of(correct("동위원소"));
    List<List<String>> accepted = List.of(List.of("isotope", "아이소토프"));

    List<List<String>> result = AcceptedAnswerSanitizer.sanitize(accepted, selections);

    assertThat(result.get(0)).containsExactly("isotope", "아이소토프");
  }
}
