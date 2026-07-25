package com.icc.qasker.quizset.grading;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * REAL_BLANK 인정 답 목록 정합성 강제 (contract.md §7.4, FR-002a). 저장 직전 authoritative하게 실행한다.
 *
 * <p>빈칸 위치별로 {@code (정답 ∪ 인정답) ∩ 함정 = ∅}을 강제해 함정 오답과 겹치는 인정 답 후보를 제거하고(보수적), outer 배열 길이를 정답 빈칸 수에
 * 정렬한다. 인정 답은 <b>원문 그대로</b> 유지하고(정규화 저장 아님), 비교 판정에만 {@link BlankAnswerNormalizer}를 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptedAnswerSanitizer {

  /**
   * @param correctContent 정답 선지 content (다중 빈칸은 콤마로 구분)
   * @param trapContents 함정 오답 선지 content 목록 (각 콤마 구분)
   * @param rawAccepted AI가 산출한 빈칸별 인정 답 후보 (원문). null 가능.
   * @return 빈칸 수에 정렬된 sanitize 결과 (외곽 index = 정답 빈칸 순서). 인정 답 없으면 빈 내부 리스트. 항상 non-null.
   */
  public static List<List<String>> sanitize(
      String correctContent, List<String> trapContents, List<List<String>> rawAccepted) {
    List<String> blanks = splitBlanks(correctContent);
    List<List<String>> result = new ArrayList<>(blanks.size());

    for (int i = 0; i < blanks.size(); i++) {
      // 정답 자신(중복 방지) + 함정 i번째 분절 → 정규화 후 인정 답에서 배제할 집합.
      Set<String> forbidden = new HashSet<>();
      forbidden.add(BlankAnswerNormalizer.normalize(blanks.get(i)));
      if (trapContents != null) {
        for (String trap : trapContents) {
          List<String> trapBlanks = splitBlanks(trap);
          if (i < trapBlanks.size()) {
            String trapNorm = BlankAnswerNormalizer.normalize(trapBlanks.get(i));
            if (!trapNorm.isEmpty()) {
              forbidden.add(trapNorm);
            }
          }
        }
      }

      result.add(sanitizeBlank(candidatesAt(rawAccepted, i), forbidden));
    }
    return result;
  }

  private static List<String> sanitizeBlank(List<String> candidates, Set<String> forbidden) {
    List<String> kept = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      String norm = BlankAnswerNormalizer.normalize(candidate);
      if (norm.isEmpty()) {
        continue; // 빈 문자열·공백/문장부호-only 후보 금지
      }
      if (forbidden.contains(norm)) {
        continue; // 정답·함정과 정규화 후 겹치면 제거 (보수적)
      }
      if (!seen.add(norm)) {
        continue; // 정규화 후 중복 제거
      }
      kept.add(candidate.strip()); // 원문 그대로 저장(양끝 공백만 정리)
    }
    return kept;
  }

  private static List<String> candidatesAt(List<List<String>> rawAccepted, int i) {
    if (rawAccepted == null || i >= rawAccepted.size() || rawAccepted.get(i) == null) {
      return List.of();
    }
    return rawAccepted.get(i);
  }

  /** 콤마 구분 정답 content를 빈칸 단위로 분절한다(양끝 공백 정리, 위치 보존). */
  private static List<String> splitBlanks(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    String[] tokens = content.split(",", -1);
    List<String> blanks = new ArrayList<>(tokens.length);
    for (String token : tokens) {
      blanks.add(token.strip());
    }
    return blanks;
  }
}
