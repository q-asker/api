package com.icc.qasker;

import com.icc.qasker.dto.response.ExplanationResponse;

public interface ExplanationService {
    
    ExplanationResponse getExplanationByProblemSetId(String problemSetId);
}

