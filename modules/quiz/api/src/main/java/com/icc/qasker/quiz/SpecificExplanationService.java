package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.response.SpecificExplanationResponse;

public interface SpecificExplanationService {

    SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId, int number);
}

