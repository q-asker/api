package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponse.SpecificExplanationResponse;

public interface SpecificExplanationService {

    SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId, int number);
}

