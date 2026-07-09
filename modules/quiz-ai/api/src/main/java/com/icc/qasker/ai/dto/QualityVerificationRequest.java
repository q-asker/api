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
    String customInstruction,
    String appliedInstruction,
    Mode mode,
    String cacheName) {

  /**
   * 캐시 없는 검증용 편의 생성자(cacheName=null). Pass 2(재검토)와 Pass 1 캐시 미생성 폴백이 사용한다. cacheName이 있으면 검증기가 해당
   * 컨텍스트 캐시(검증 루브릭+PDF 원문)를 참조해 원문 대조 검증을 수행한다.
   */
  public QualityVerificationRequest(
      String quizType,
      String language,
      String question,
      List<Selection> selections,
      String modelAnswer,
      String customInstruction,
      String appliedInstruction,
      Mode mode) {
    this(
        quizType,
        language,
        question,
        selections,
        modelAnswer,
        customInstruction,
        appliedInstruction,
        mode,
        null);
  }

  public record Selection(String content, boolean correct) {}

  /** 검증 강도. PASS_1=생성 게이트(경량), PASS_2=승인 재검토(엄격·포괄). */
  public enum Mode {
    PASS_1,
    PASS_2
  }
}
