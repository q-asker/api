package com.icc.qasker.quizset.dto.readonly;

/** 문항 좌표(세트 id + 문항 번호). 최초 조상을 가리키면 세대가 반복돼도 같은 값이라 중복 제거 키가 된다. */
public record ProblemLineage(Long problemSetId, int number) {}
