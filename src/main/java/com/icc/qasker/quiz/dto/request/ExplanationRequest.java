package com.icc.qasker.quiz.dto.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExplanationRequest {
    private Long problemSetId;
    private List<AnswerRequest> answers;
}
