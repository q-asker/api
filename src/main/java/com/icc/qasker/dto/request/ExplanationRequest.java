package com.icc.qasker.dto.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExplanationRequest {
    private List<AnswerRequest> answers;
}
