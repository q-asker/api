package com.icc.qasker.ai.dto;

import java.util.List;

/** {@code acceptedAnswers}는 REAL_BLANK 정답 선지의 빈칸별 인정 답 목록(원문). 그 외에는 null. */
public record AISelection(
    String content, String explanation, boolean correct, List<List<String>> acceptedAnswers) {

  public AISelection(String content, String explanation, boolean correct) {
    this(content, explanation, correct, null);
  }
}
