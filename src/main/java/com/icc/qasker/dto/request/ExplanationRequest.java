package com.icc.qasker.dto.request;

import lombok.Getter;
import java.util.List;
@Getter
public class ExplanationRequest {
    private List<AnswerRequest> answers;
}
