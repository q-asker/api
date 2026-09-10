package com.icc.qasker.global.quiz;

/**
 * 퀴즈 유형. 생성(quiz-ai)·저장(quiz-set)·풀이(quiz-history)가 같은 상수를 쓴다.
 *
 * <p>모듈마다 따로 정의하면 경계를 문자열로 넘게 되고, 그 변환이 실패해도 폴백에 흡수돼 조용히 다른 유형으로 처리된다. 그래서 여기 하나만 둔다.
 *
 * <p>이름은 DB에 그대로 저장된다(@Enumerated(EnumType.STRING)) — 상수명을 바꾸면 마이그레이션이 필요하다.
 */
public enum QuizType {
  MULTIPLE,
  BLANK,
  REAL_BLANK,
  OX,
  ESSAY
}
