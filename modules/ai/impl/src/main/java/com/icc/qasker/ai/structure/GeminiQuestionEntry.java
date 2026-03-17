package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** 스트리밍 분리 응답용 문제 엔트리 (해설 없음). */
public record GeminiQuestionEntry(
    @JsonPropertyDescription("문제 번호 (1부터 시작)") int number,
    @JsonPropertyDescription("문제 내용") String content,
    @JsonPropertyDescription("선택지 목록") List<GeminiQuestionSelection> selections) {}
