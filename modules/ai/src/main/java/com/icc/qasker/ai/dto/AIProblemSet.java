package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record AIProblemSet(
    @JsonPropertyDescription("생성된 퀴즈 문제들의 목록입니다.")
    List<AIProblem> quiz
) {

}