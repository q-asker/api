package com.icc.qasker.quiz.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExplanationResponse {

    private List<ResultResponse> results;
}
