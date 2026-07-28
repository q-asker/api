package com.icc.qasker.quizset.dto.readonly;

import java.util.List;

/**
 * Selection Entity의 read-only DTO. 모듈 경계를 넘어 Selection 데이터를 전달할 때 사용. {@code acceptedAnswers}는
 * REAL_BLANK 서버 채점용 인정 집합(빈칸별)이며, 정보가 없으면 {@code null}이다.
 */
public record SelectionDetail(String content, boolean correct, List<List<String>> acceptedAnswers) {

  public SelectionDetail(String content, boolean correct) {
    this(content, correct, null);
  }
}
