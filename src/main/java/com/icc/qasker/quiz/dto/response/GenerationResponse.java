package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenerationResponse {

    private String problemSetId;

    public static GenerationResponse of(String problemSetId) {
        return new GenerationResponse(problemSetId);
    }

}
