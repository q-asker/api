package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 문항 생성 근거. 생성기가 문항과 함께 산출하며, 품질 게이트(Pass 1)·재검토(Pass 2)의 검증 입력이 된다. BeanOutputConverter가 이 레코드에서
 * JSON 스키마를 자동 파생하므로 GeminiResponseSchema 수정은 불필요하다.
 *
 * <p>객관형(MULTIPLE/OX/BLANK)은 selfChecks를, 서술형(ESSAY)은 modelAnswerBasis·rubricConsistency를 채운다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRationale(
    @JsonPropertyDescription("정답/내용의 원문 근거 (페이지·섹션·인용)") SourceAnchor sourceAnchor,
    @JsonPropertyDescription("이 문항이 겨냥한 학습목표") String learningObjective,
    @JsonPropertyDescription("Bloom's 인지수준 (REMEMBER/UNDERSTAND/APPLY/ANALYZE/EVALUATE/CREATE)")
        String bloomLevel,
    @JsonPropertyDescription("자기 난이도 추정 (0.0~1.0)") Double difficultyEstimate,
    @JsonPropertyDescription("선정한 문제 구성전략 — 무엇을 왜 (유형별 GuideLine 부합 근거)")
        String constructionStrategy,
    @JsonPropertyDescription("사용자 지시(customInstruction) 반영 내역") String instructionApplication,
    @JsonPropertyDescription("생성기 자기신뢰도 (0.0~1.0)") Double confidence,
    @JsonPropertyDescription("객관형 자기 점검 항목") SelfChecks selfChecks,
    @JsonPropertyDescription("[ESSAY] 모범답안의 근거") String modelAnswerBasis,
    @JsonPropertyDescription("[ESSAY] 질문↔모범답안↔채점 루브릭 3자 정합 여부") Boolean rubricConsistency) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SourceAnchor(
      @JsonPropertyDescription("원문 페이지 번호") Integer page,
      @JsonPropertyDescription("원문 섹션/제목") String section,
      @JsonPropertyDescription("근거가 되는 원문 인용구") String quote) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SelfChecks(
      @JsonPropertyDescription("정답이 유일한가") Boolean singleCorrectAnswer,
      @JsonPropertyDescription("정답이 sourceAnchor 원문에 근거하는가") Boolean answerGroundedInSource,
      @JsonPropertyDescription("오답(오답지)이 그럴듯한가") Boolean distractorsPlausible,
      @JsonPropertyDescription("문항 밖 지식 없이 풀 수 있는가") Boolean noOutsideKnowledge) {}
}
