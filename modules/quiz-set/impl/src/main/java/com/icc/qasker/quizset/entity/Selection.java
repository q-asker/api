package com.icc.qasker.quizset.entity;

import java.util.List;

/**
 * 선택지. REAL_BLANK 정답 선택지는 {@code acceptedAnswers}에 빈칸별 인정 표현 집합을 담는다(서버 채점용). 이 정보가 없는 문항(구 문항·타
 * 유형)은 {@code null}이며, 채점 시 정답 content 기반 폴백으로 동작한다.
 */
public record Selection(
    String content, String explanation, boolean correct, List<List<String>> acceptedAnswers) {

  public Selection(String content, String explanation, boolean correct) {
    this(content, explanation, correct, null);
  }
}
