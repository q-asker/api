package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feResponse.SpecificExplanationResponse;

public interface SpecificExplanationService {

    SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId, int number);
}

