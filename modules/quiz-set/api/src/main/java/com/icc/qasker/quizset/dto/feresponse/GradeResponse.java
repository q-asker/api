package com.icc.qasker.quizset.dto.feresponse;

import java.util.List;

/**
 * REAL_BLANK 무상태 채점 결과. 문항별 정오답·표시용 대표정답과 함께, 채점 후 각 빈칸의 허용 정답 목록을 노출한다(FR-006). 풀이 화면 응답엔 담기지 않으며
 * 이 채점 응답에만 실린다(FR-007).
 */
public record GradeResponse(List<GradeResult> results) {

  /**
   * {@code answer}는 표시용 대표정답(다중 빈칸은 저장 규약대로 ", " 결합된 content). {@code acceptedAnswers}는 빈칸별 허용 정답
   * 목록(바깥 배열=빈칸 등장 순서, index 0=canonical 모범답, 이하 통용 변형)이며, 인정 집합이 없는 과거 세트는 대표정답 content 기반 폴백이
   * 담긴다(FR-008·FR-009).
   */
  public record GradeResult(
      int number, boolean isCorrect, String answer, List<List<String>> acceptedAnswers) {}
}
