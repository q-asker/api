package com.icc.qasker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResultResponse {
    private Long problemId;
    private String correctAnswer;
    private boolean isCorrect;
    private String explanation;
}
