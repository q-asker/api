package com.icc.qasker.ai.dto;

import java.util.List;

/**
 * Phase 2(해설) 배치 결과 1건. 문항 번호로 저장된 문제와 대응하며, 선지별 해설은 <b>저장된 선지 순서</b>와 동일한 인덱스를 가진다.
 *
 * <p>오케스트레이터가 Phase 1에서 최종 정렬(셔플/정규화)된 순서로 문제를 저장하므로, 그 순서 그대로 해설을 생성해 정렬 불일치가 없다.
 *
 * @param number 세트 내 문항 번호 (saveProblem 반환값)
 * @param selectionExplanations 선지별 해설 — 저장된 선지 순서와 동일 인덱스
 */
public record AIExplanation(int number, List<String> selectionExplanations) {}
