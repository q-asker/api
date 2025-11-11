package com.icc.qasker;

import com.icc.qasker.dto.response.GenerationResponse;
import reactor.core.publisher.Mono;

public interface GenerationMockService {
    
    Mono<GenerationResponse> processGenerationRequest(FeGenerationMockRequest feGenerationMockRequest);
}

