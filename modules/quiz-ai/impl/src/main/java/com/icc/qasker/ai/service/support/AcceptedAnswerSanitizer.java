package com.icc.qasker.ai.service.support;

import com.icc.qasker.ai.dto.AISelection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * REAL_BLANK 허용변형(동의어) 생성 후처리 필터. 오답 보호(FR-005)의 백엔드측 보장: 생성된 허용변형이 그 문항의 오답선지와 정규화 후 일치하면 저장 전에
 * 제거해, 저장 시점에 <b>허용변형 ∩ 오답선지 = ∅</b>을 강제한다. 빈 문자열·중복도 함께 정리한다. 판정 자체는 클라이언트가 하므로 여기 정규화는 "오답선지와 표기가
 * 같은지"만 가려내는 보수적 수준(트림·소문자·내부 공백 단일화)으로 둔다 — 과도하게 정규화하면 정당한 동의어까지 오답선지로 오인해 제거할 수 있다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptedAnswerSanitizer {

  /**
   * @param acceptedAnswers 빈칸 순서대로의 허용변형 목록(빈칸 i → 동의어들). null이면 null 반환(허용변형 미생성).
   * @param selections 같은 문항의 선지들(정답+오답). 오답선지(correct==false) content의 콤마 토큰이 필터 기준.
   * @return 오답선지와 겹치는 변형·빈 문자열·중복을 제거한 목록. 빈칸별 구조·순서는 보존한다.
   */
  public static List<List<String>> sanitize(
      List<List<String>> acceptedAnswers, List<AISelection> selections) {
    if (acceptedAnswers == null) {
      return null;
    }
    Set<String> distractorTokens = collectDistractorTokens(selections);

    List<List<String>> result = new ArrayList<>(acceptedAnswers.size());
    for (List<String> perBlank : acceptedAnswers) {
      List<String> cleaned = new ArrayList<>();
      Set<String> seen = new LinkedHashSet<>();
      if (perBlank != null) {
        for (String variant : perBlank) {
          if (variant == null) {
            continue;
          }
          String norm = normalize(variant);
          if (norm.isEmpty() || distractorTokens.contains(norm) || !seen.add(norm)) {
            continue;
          }
          cleaned.add(variant.trim());
        }
      }
      result.add(cleaned);
    }
    return result;
  }

  /** 오답선지(correct==false)의 content를 콤마 분리해 정규화한 토큰 집합. 다중 빈칸 오답도 위치 무관하게 모두 담는다(보수적). */
  private static Set<String> collectDistractorTokens(List<AISelection> selections) {
    Set<String> tokens = new LinkedHashSet<>();
    if (selections == null) {
      return tokens;
    }
    for (AISelection selection : selections) {
      if (selection == null || selection.correct() || selection.content() == null) {
        continue;
      }
      for (String token : selection.content().split(",")) {
        String norm = normalize(token);
        if (!norm.isEmpty()) {
          tokens.add(norm);
        }
      }
    }
    return tokens;
  }

  private static String normalize(String value) {
    return value.trim().replaceAll("\\s+", " ").toLowerCase();
  }
}
