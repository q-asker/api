package com.icc.qasker.ai.dto;

import java.util.List;

/**
 * 문항 품질 검증 요청(api 계약). 생성 게이트(Pass 1, quiz-ai)와 승인 재검토(Pass 2, quiz-set)가 공통으로 사용한다. GuideLine 등
 * 유형별 맥락은 구현이 quizType으로 내부 해석하므로 요청에는 유형 식별자만 담는다.
 */
public record QualityVerificationRequest(
    String quizType,
    String language,
    String question,
    List<Selection> selections,
    String modelAnswer,
    AIRationale rationale,
    String customInstruction,
    String appliedInstruction,
    Mode mode) {

  public record Selection(String content, boolean correct) {}

  /** 검증 강도. PASS_1=생성 게이트(경량), PASS_2=승인 재검토(엄격·포괄). */
  public enum Mode {
    PASS_1,
    PASS_2
  }
}
