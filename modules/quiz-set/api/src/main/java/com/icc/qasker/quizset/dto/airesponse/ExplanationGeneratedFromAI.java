package com.icc.qasker.quizset.dto.airesponse;

import java.util.List;

/**
 * Phase 2(해설 후속 생성) 결과 전달용 DTO.
 *
 * @param number 대상 문항 번호 (세트 내 번호)
 * @param explanation 문항 해설 (조립된 마크다운)
 * @param selectionExplanations 선지별 해설 — 선지 순서와 동일한 인덱스, 크기가 선지 수와 다르면 선지 해설은 갱신하지 않는다
 */
public record ExplanationGeneratedFromAI(
    int number, String explanation, List<String> selectionExplanations) {}
