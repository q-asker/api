package com.icc.qasker.quizset.dto.feresponse;

import java.util.List;

/** REAL_BLANK 무상태 채점 결과. 문항별 정오답과 표시용 대표정답을 담는다. 인정 집합(acceptedAnswers)은 노출하지 않는다. */
public record GradeResponse(List<GradeResult> results) {

  /** {@code answer}는 표시용 대표정답(다중 빈칸은 저장 규약대로 ", " 결합된 content). */
  public record GradeResult(int number, boolean isCorrect, String answer) {}
}
