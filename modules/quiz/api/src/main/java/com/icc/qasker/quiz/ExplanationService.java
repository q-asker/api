package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponset.ExplanationResponse;

public interface ExplanationService {

    ExplanationResponse getExplanationByProblemSetId(String problemSetId);
}

