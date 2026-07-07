package com.icc.qasker.ai.dto;

/**
 * 문항 생성 근거 DTO. 구조(structure) 계층에서 산출된 근거를 저장 계층으로 전파하는 중간 표현이다. 객관형은 selfChecks를, 서술형(ESSAY)은
 * modelAnswerBasis·rubricConsistency를 채우며 해당 없는 필드는 null이다.
 */
public record AIRationale(
    SourceAnchor sourceAnchor,
    String learningObjective,
    String bloomLevel,
    Double difficultyEstimate,
    String constructionStrategy,
    String instructionApplication,
    Double confidence,
    SelfChecks selfChecks,
    String modelAnswerBasis,
    Boolean rubricConsistency) {

  public record SourceAnchor(Integer page, String section, String quote) {}

  public record SelfChecks(
      Boolean singleCorrectAnswer,
      Boolean answerGroundedInSource,
      Boolean distractorsPlausible,
      Boolean noOutsideKnowledge) {}
}
