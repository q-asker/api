package com.icc.qasker.quizset.entity;

import java.util.List;

/**
 * 선지. {@code acceptedAnswers}는 REAL_BLANK 정답 선지에만 채워지는 빈칸별 인정 답 목록(contract.md §7.2) — 외곽 index =
 * 정답 content의 콤마 분절(빈칸) 순서, 내부 = 그 빈칸의 인정 변형(원문 저장). 비 REAL_BLANK·오답 선지·기존 세트에서는 {@code null}이며, 이
 * 경우 프론트는 정규화 관용만 적용한다(FR-006).
 */
public record Selection(
    String content, String explanation, boolean correct, List<List<String>> acceptedAnswers) {

  /** 인정 답 목록이 없는 선지용 편의 생성자(기존 호출부·기존 JSON 하위호환). */
  public Selection(String content, String explanation, boolean correct) {
    this(content, explanation, correct, null);
  }
}
