package com.icc.qasker.ai.dto;

import java.util.List;

/** AI가 생성한 선택지. REAL_BLANK 정답 선택지는 {@code acceptedAnswers}에 빈칸별 인정 표현 집합을 담는다(없으면 null). */
public record AISelection(
    String content, String explanation, boolean correct, List<List<String>> acceptedAnswers) {

  public AISelection(String content, String explanation, boolean correct) {
    this(content, explanation, correct, null);
  }
}
