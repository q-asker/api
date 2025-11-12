package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;

public interface GenerationService {

    GenerationResponse processGenerationRequest(FeGenerationRequest feGenerationRequest);
}

