package com.icc.qasker.quizset.dto.ferequest.enums;

public enum QuizType {
  MULTIPLE,
  BLANK,
  REAL_BLANK,
  OX,
  ESSAY;

  public String toAiStrategyName() {
    return name();
  }
}
