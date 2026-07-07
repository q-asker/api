package com.icc.qasker.quizset.entity;

/**
 * 문항 생성 근거(JSON 값 객체). Problem에 @JdbcTypeCode(SqlTypes.JSON)으로 내장되며, 품질 검증·재생성의 입력이 된다. 원문 삭제 후에도
 * 잔존한다.
 *
 * <p>전 유형 공통 필드에 더해, 객관형(MULTIPLE/OX/BLANK)은 {@link SelfChecks}, 서술형(ESSAY)은
 * modelAnswerBasis·rubricConsistency를 채운다. 유형에 해당하지 않는 필드는 null이다. 필드 결측은 검증 시 해당 항목 실패로 처리될 수 있다.
 */
public record Rationale(
    // --- 공통 ---
    SourceAnchor sourceAnchor,
    String learningObjective,
    String bloomLevel,
    Double difficultyEstimate,
    String constructionStrategy,
    String instructionApplication,
    Double confidence,
    // --- 객관형(MULTIPLE/OX/BLANK) ---
    SelfChecks selfChecks,
    // --- 서술형(ESSAY) ---
    String modelAnswerBasis,
    Boolean rubricConsistency) {

  /** 정답/내용의 원문 근거(위치·인용). Pass 2에서 인용↔정답 정합 검증에 사용. */
  public record SourceAnchor(Integer page, String section, String quote) {}

  /** 객관형 생성기 자기 점검 항목. */
  public record SelfChecks(
      Boolean singleCorrectAnswer,
      Boolean answerGroundedInSource,
      Boolean distractorsPlausible,
      Boolean noOutsideKnowledge) {}
}
