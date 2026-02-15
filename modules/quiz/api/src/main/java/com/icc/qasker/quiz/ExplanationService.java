package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponse.ExplanationResponse;

public interface ExplanationService {

    ExplanationResponse getExplanationByProblemSetId(String problemSetId);
}

