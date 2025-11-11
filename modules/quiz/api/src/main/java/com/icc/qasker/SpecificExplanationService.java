package com.icc.qasker;

import com.icc.qasker.dto.response.SpecificExplanationResponse;

public interface SpecificExplanationService {
    
    SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId, int number);
}

