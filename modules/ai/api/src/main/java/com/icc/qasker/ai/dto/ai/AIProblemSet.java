package com.icc.qasker.ai.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;


public record AIProblemSet(

    @JsonPropertyDescription("생성된 퀴즈 문제 목록")
    List<AIProblem> quiz

) {

}
