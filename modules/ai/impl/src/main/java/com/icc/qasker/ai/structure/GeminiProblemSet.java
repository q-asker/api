package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record GeminiProblemSet(

    @JsonPropertyDescription("생성된 퀴즈 문제 목록")
    List<GeminiProblem> quiz

) {

}
