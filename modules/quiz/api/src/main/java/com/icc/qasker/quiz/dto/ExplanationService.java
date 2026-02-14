package com.icc.qasker.quiz.dto;

import com.icc.qasker.quiz.dto.feResponse.ExplanationResponse;

public interface ExplanationService {

    ExplanationResponse getExplanationByProblemSetId(String problemSetId);
}

