package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import reactor.core.publisher.Mono;

public interface GenerationService {

    Mono<GenerationResponse> processGenerationRequest(FeGenerationRequest feGenerationRequest);
}

