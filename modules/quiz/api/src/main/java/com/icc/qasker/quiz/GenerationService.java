package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;

public interface GenerationService {

    GenerationSessionResponse triggerGeneration(
        String useId,
        GenerationRequest request
    );
}

