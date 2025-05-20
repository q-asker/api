package com.icc.qasker.dto.request;

import lombok.Getter;

@Getter
public class AnswerRequest {
    private Long problemId;
    private String userAnswer;
}
