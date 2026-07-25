package com.icc.qasker.quizset.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** contract.md §7.4 (FR-002a) 인정 답 sanitize 검증. */
class AcceptedAnswerSanitizerTest {

  @Test
  @DisplayName("단일 빈칸: 정규화 후 서로 다른 동의어는 모두 유지된다")
  void keepsSynonymsSingleBlank() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize(
            "이벤트 루프", List.of("스택", "힙"), List.of(List.of("event loop", "EL")));
    assertThat(result).hasSize(1);
    // "event loop"→"eventloop", "EL"→"el" — 정답 "이벤트루프"·서로와 모두 달라 둘 다 유지.
    assertThat(result.get(0)).containsExactly("event loop", "EL");
  }

  @Test
  @DisplayName("정규화 후 동일한 후보는 중복 제거된다")
  void dedupByNormalizedForm() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize(
            "이벤트 루프", List.of(), List.of(List.of("event loop", "Event Loop", "EVENTLOOP")));
    // "event loop","Event Loop","EVENTLOOP" 는 모두 normalize → "eventloop" → 첫 원문만 유지
    assertThat(result.get(0)).containsExactly("event loop");
  }

  @Test
  @DisplayName("함정 오답과 정규화 후 겹치는 후보는 제거된다(FR-002a)")
  void dropsTrapCollisions() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize(
            "능동수송",
            List.of("수동수송", "촉진확산"),
            List.of(List.of("active transport", "수동 수송"))); // "수동 수송" → 함정 "수동수송"과 충돌 → 제거
    assertThat(result.get(0)).containsExactly("active transport");
  }

  @Test
  @DisplayName("정답 자신과 겹치는 후보는 제거된다(목록 미포함 원칙)")
  void dropsModelDuplicate() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize(
            "미토콘드리아", List.of("엽록체"), List.of(List.of("미토콘드리아", "mitochondria")));
    assertThat(result.get(0)).containsExactly("mitochondria");
  }

  @Test
  @DisplayName("빈 문자열·공백/문장부호-only 후보는 제거된다")
  void dropsEmpty() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize("정답", List.of(), List.of(List.of("", "  ", "...", "유효")));
    assertThat(result.get(0)).containsExactly("유효");
  }

  @Test
  @DisplayName("다중 빈칸: 빈칸별로 독립 정렬·판정된다")
  void multiBlankAligned() {
    List<List<String>> result =
        AcceptedAnswerSanitizer.sanitize(
            "감수분열, 체세포분열",
            List.of("체세포분열, 감수분열"), // 위치역전 함정
            List.of(List.of("meiosis"), List.of("mitosis")));
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsExactly("meiosis");
    assertThat(result.get(1)).containsExactly("mitosis");
  }

  @Test
  @DisplayName("outer 배열 길이는 정답 빈칸 수에 정렬된다(초과분 버림·부족분 빈 리스트)")
  void alignsOuterLength() {
    // 정답 2빈칸인데 AI가 3개 blank·1개 blank 준 경우 각각 정렬
    List<List<String>> over =
        AcceptedAnswerSanitizer.sanitize(
            "A, B", List.of(), List.of(List.of("a1"), List.of("b1"), List.of("c1")));
    assertThat(over).hasSize(2);

    List<List<String>> under =
        AcceptedAnswerSanitizer.sanitize("A, B", List.of(), List.of(List.of("a1")));
    assertThat(under).hasSize(2);
    assertThat(under.get(1)).isEmpty();
  }

  @Test
  @DisplayName("rawAccepted가 null이어도 빈칸 수만큼 빈 리스트를 반환한다(non-null)")
  void nullRawYieldsEmptyPerBlank() {
    List<List<String>> result = AcceptedAnswerSanitizer.sanitize("A, B", List.of(), null);
    assertThat(result).hasSize(2);
    assertThat(result).allSatisfy(blank -> assertThat(blank).isEmpty());
  }
}
