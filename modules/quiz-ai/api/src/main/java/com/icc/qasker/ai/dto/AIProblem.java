package com.icc.qasker.ai.dto;

import java.util.List;

public record AIProblem(
    String content,
    String bloomsLevel,
    List<AISelection> selections,
    List<Integer> referencedPages,
    String appliedInstruction,
    AIRationale rationale,
    QualityMark qualityMark) {

  /** 품질 판정 결과를 부여한 새 인스턴스를 반환한다(생성 게이트에서 사용). */
  public AIProblem withQuality(String status, String feedback) {
    return new AIProblem(
        content,
        bloomsLevel,
        selections,
        referencedPages,
        appliedInstruction,
        rationale,
        new QualityMark(status, feedback));
  }
}
