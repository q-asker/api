package com.icc.qasker.quizset.dto.readonly;

import java.util.List;

/**
 * Problem Entity의 read-only DTO. 모듈 경계를 넘어 Problem 데이터를 전달할 때 사용. {@code acceptedAnswers}는
 * REAL_BLANK 정답 선지의 빈칸별 인정 답(quiz-level lift, contract.md §7.2). 그 외 null.
 */
public record ProblemDetail(
    int number,
    String title,
    List<SelectionDetail> selections,
    String explanationContent,
    List<List<String>> acceptedAnswers) {

  /** 인정 답 목록이 없는(비 REAL_BLANK·기존 세트) 상세용 편의 생성자. */
  public ProblemDetail(
      int number, String title, List<SelectionDetail> selections, String explanationContent) {
    this(number, title, selections, explanationContent, null);
  }
}
