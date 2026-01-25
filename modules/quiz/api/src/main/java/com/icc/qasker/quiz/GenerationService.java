package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationResponse;
import reactor.core.publisher.Flux;

public interface GenerationService {

    Flux<GenerationResponse> processGenerationRequest(GenerationRequest generationRequest,
        String userId);
}

