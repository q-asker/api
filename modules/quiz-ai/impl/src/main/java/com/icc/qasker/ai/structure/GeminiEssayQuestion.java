package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiEssayQuestion(
    @JsonPropertyDescription("질문문") String content,
    @JsonPropertyDescription("이 문항에 적용된 Bloom's 수준") String bloomsLevel,
    @JsonPropertyDescription("참조한 강의노트 페이지 번호") List<Integer> referencedPages,
    @JsonPropertyDescription("모범답안 (핵심 요소 포함)") String modelAnswer,
    @JsonPropertyDescription("해설 (분석적 루브릭 포함)") String explanation,
    @JsonPropertyDescription("사용자 지시 반영 결과") String appliedInstruction) {}
