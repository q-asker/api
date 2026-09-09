package com.icc.qasker.quizset.dto.readonly;

import java.util.List;

/**
 * Problem Entity의 read-only DTO. 모듈 경계를 넘어 Problem 데이터를 전달할 때 사용. {@code originProblemSetId}/{@code
 * originNumber}는 재출제된 문항이 가리키는 최초 조상이며, 자료로 생성된 원본 문항은 둘 다 null이다.
 */
public record ProblemDetail(
    int number,
    String title,
    List<SelectionDetail> selections,
    String explanationContent,
    Long originProblemSetId,
    Integer originNumber) {

  public ProblemDetail(
      int number, String title, List<SelectionDetail> selections, String explanationContent) {
    this(number, title, selections, explanationContent, null, null);
  }

  /** 중복 제거용 문항 신원 — 혈통이 있으면 최초 조상을, 없으면 자기 자신을 가리킨다. */
  public ProblemLineage lineage(Long owningProblemSetId) {
    return new ProblemLineage(
        originProblemSetId == null ? owningProblemSetId : originProblemSetId,
        originNumber == null ? number : originNumber);
  }
}
