package com.icc.qasker.quizset.grading;

import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * REAL_BLANK 채점의 단일 진실원천(SSOT). 정규화 + 폐쇄 집합 멤버십으로 결정적으로 판정한다(AI 호출 없음).
 *
 * <p>결과·해설·기록 세 화면과 로그인/비로그인이 모두 이 클래스를 경유해 동일 판정을 얻는다(FR-006). 애매한 답은 인정 집합 밖이므로 자동으로 오답이
 * 된다(FR-005, 보수적 기본).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RealBlankGrader {

  /** 다중 빈칸 사용자 입력 직렬화 구분자(U+001F, Unit Separator). 프론트는 다중일 때만 이 구분자로 결합한다. */
  public static final String BLANK_DELIMITER = "\u001F";

  /** 저장된 정답 content의 빈칸 구분자(생성 규약: "콤마(,)와 공백 한 칸"). 방어적으로 콤마 기준 분해 후 trim. */
  private static final String CONTENT_BLANK_DELIMITER = ",";

  /**
   * 정규화 규칙(SSOT): NFKC(전각/반각·호환문자) → 소문자 → 공백·문장부호 제거. 표기 차이(FR-001)만 있는 답을 같은 문자열로 접는다.
   * 동의어(FR-002)는 정규화가 아니라 인정 집합(acceptedAnswers)이 담당한다.
   */
  public static String normalize(String s) {
    if (s == null) {
      return "";
    }
    String normalized = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder(normalized.length());
    normalized
        .codePoints()
        .forEach(
            cp -> {
              if (Character.isWhitespace(cp) || isPunctuation(cp)) {
                return;
              }
              sb.appendCodePoint(cp);
            });
    return sb.toString();
  }

  private static boolean isPunctuation(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION,
          Character.DASH_PUNCTUATION,
          Character.START_PUNCTUATION,
          Character.END_PUNCTUATION,
          Character.INITIAL_QUOTE_PUNCTUATION,
          Character.FINAL_QUOTE_PUNCTUATION,
          Character.OTHER_PUNCTUATION ->
          true;
      default -> false;
    };
  }

  /**
   * 빈칸별 독립 판정 후 전 빈칸 AND. 조각 수가 인정 집합의 빈칸 수와 다르면(빈 답 포함) 오답으로 처리한다(FR-005 보수적).
   *
   * @param inputsPerBlank 빈칸 등장 순서대로의 사용자 입력 조각
   * @param acceptedPerBlank 빈칸별 인정 표현 집합(바깥=빈칸 순서, 안=그 빈칸의 인정 표현들)
   */
  public static boolean isCorrect(
      List<String> inputsPerBlank, List<List<String>> acceptedPerBlank) {
    if (inputsPerBlank == null || acceptedPerBlank == null) {
      return false;
    }
    if (inputsPerBlank.size() != acceptedPerBlank.size() || acceptedPerBlank.isEmpty()) {
      return false;
    }
    for (int i = 0; i < acceptedPerBlank.size(); i++) {
      if (!matchesBlank(inputsPerBlank.get(i), acceptedPerBlank.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesBlank(String input, List<String> accepted) {
    if (accepted == null || accepted.isEmpty()) {
      return false;
    }
    String normalizedInput = normalize(input);
    if (normalizedInput.isEmpty()) {
      return false;
    }
    for (String candidate : accepted) {
      if (normalizedInput.equals(normalize(candidate))) {
        return true;
      }
    }
    return false;
  }

  /** 직렬화된 사용자 입력(textAnswer)을 빈칸별 조각으로 분해한다. 단일 빈칸(구분자 없음)은 1조각. trailing 빈 조각 유지. */
  public static List<String> splitInputs(String textAnswer) {
    if (textAnswer == null) {
      return List.of();
    }
    return Arrays.asList(textAnswer.split(BLANK_DELIMITER, -1));
  }

  /**
   * 인정 집합 정보가 없는 문항(구 문항 등)의 폴백: 정답 content를 빈칸별 단일 인정값으로 쓴다(현행 완전 일치 수준 보장, FR-009). content가 콤마
   * 결합이면 각 빈칸으로 분해한다.
   */
  public static List<List<String>> fallbackAccepted(String correctContent) {
    if (correctContent == null || correctContent.isBlank()) {
      return List.of();
    }
    return Arrays.stream(correctContent.split(CONTENT_BLANK_DELIMITER, -1))
        .map(String::trim)
        .<List<String>>map(List::of)
        .toList();
  }

  /**
   * 문항 선택지 목록과 사용자 입력으로 판정한다. 정답 선택지(correct=true)의 인정 집합(없으면 content 기반 폴백)으로 채점하고, 표시용 대표정답(정답
   * content)을 함께 돌려준다. 결과·기록 양쪽이 이 한 메서드를 경유한다(SSOT).
   */
  public static GradeOutcome grade(List<SelectionDetail> selections, String textAnswer) {
    SelectionDetail correct =
        selections == null
            ? null
            : selections.stream().filter(SelectionDetail::correct).findFirst().orElse(null);
    String answer = correct == null ? "" : correct.content();
    List<List<String>> accepted =
        canonicalize(
            answer,
            correct != null && correct.acceptedAnswers() != null
                ? correct.acceptedAnswers()
                : fallbackAccepted(answer));
    boolean isCorrect = isCorrect(splitInputs(textAnswer), accepted);
    return new GradeOutcome(isCorrect, answer, accepted);
  }

  /**
   * 각 빈칸 인정 집합의 index 0을 canonical 모범답(대표정답의 그 빈칸)으로 고정한다(FR-006 노출 관례). 모범답이 집합에 없으면 앞에 넣고, 이미
   * 있으면(정규화 동일) 맨 앞으로 끌어올린다 — 모범답은 언제나 정답이어야 하므로 채점 멤버십도 함께 보장된다. 대표정답 빈칸 수보다 인정 집합이 길면 초과분은 원본 순서를
   * 유지한다.
   */
  private static List<List<String>> canonicalize(String answer, List<List<String>> accepted) {
    if (accepted == null || accepted.isEmpty()) {
      return accepted;
    }
    String[] answerBlanks = (answer == null ? "" : answer).split(CONTENT_BLANK_DELIMITER, -1);
    List<List<String>> result = new ArrayList<>(accepted.size());
    for (int i = 0; i < accepted.size(); i++) {
      List<String> blank = accepted.get(i);
      String canonical = i < answerBlanks.length ? answerBlanks[i].trim() : "";
      if (blank == null || canonical.isEmpty()) {
        result.add(blank);
        continue;
      }
      String normalizedCanonical = normalize(canonical);
      List<String> reordered = new ArrayList<>(blank.size() + 1);
      reordered.add(canonical);
      for (String variant : blank) {
        if (variant != null && !normalize(variant).equals(normalizedCanonical)) {
          reordered.add(variant);
        }
      }
      result.add(List.copyOf(reordered));
    }
    return List.copyOf(result);
  }

  /**
   * 채점 결과: 정오답 + 표시용 대표정답 + 빈칸별 인정 집합. {@code acceptedAnswers}는 채점에 실제로 쓰인 집합(없으면 content 기반 폴백)이며,
   * 각 빈칸 배열의 index 0은 canonical 모범답이다(FR-006 노출용).
   */
  public record GradeOutcome(
      boolean isCorrect, String answer, List<List<String>> acceptedAnswers) {}
}
