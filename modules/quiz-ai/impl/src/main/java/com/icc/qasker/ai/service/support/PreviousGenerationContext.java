package com.icc.qasker.ai.service.support;

import com.icc.qasker.ai.dto.AIProblem;
import java.util.ArrayList;
import java.util.List;

/**
 * 청크 K(K≥2) 호출에 주입할 직전 누적 문항 요약. MULTIPLE/OX/BLANK 등 모든 청크 루프 오케스트레이터가 공용으로 사용한다.
 *
 * <p>slot 슬라이딩 윈도우로 stem 요약을 절단하여 마지막 청크가 토큰 폭증을 만나지 않게 한다(분쟁 D2).
 */
public record PreviousGenerationContext(List<Item> items) {

  /** stem 절단 길이(글자) */
  private static final int STEM_MAX_CHARS = 100;

  /** 유지할 최근 항목 수(슬라이딩 윈도우) */
  private static final int WINDOW_SIZE = 20;

  public record Item(int quizNumber, String stemSummary, int answerIndex, String topicKeyword) {}

  /** AIProblem 누적 결과로부터 컨텍스트를 만든다. 가장 최근 WINDOW_SIZE개만 보존. */
  public static PreviousGenerationContext from(List<AIProblem> accumulated) {
    if (accumulated == null || accumulated.isEmpty())
      return new PreviousGenerationContext(List.of());

    int start = Math.max(0, accumulated.size() - WINDOW_SIZE);
    List<Item> items = new ArrayList<>(accumulated.size() - start);
    for (int i = start; i < accumulated.size(); i++) {
      AIProblem p = accumulated.get(i);
      items.add(
          new Item(
              i + 1, truncate(p.content(), STEM_MAX_CHARS), answerIndexOf(p), topicKeywordOf(p)));
    }
    return new PreviousGenerationContext(List.copyOf(items));
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    String trimmed = s.replaceAll("\\s+", " ").trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  private static int answerIndexOf(AIProblem p) {
    if (p.selections() == null) return -1;
    for (int i = 0; i < p.selections().size(); i++) {
      if (p.selections().get(i).correct()) return i;
    }
    return -1;
  }

  private static String topicKeywordOf(AIProblem p) {
    if (p.appliedInstruction() != null && !p.appliedInstruction().isBlank()) {
      return truncate(p.appliedInstruction(), 30);
    }
    return truncate(p.content(), 20);
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }
}
