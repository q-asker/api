package com.icc.qasker;

import com.icc.qasker.dto.request.FeGenerationRequest;
import com.icc.qasker.dto.response.GenerationResponse;
import reactor.core.publisher.Mono;

public interface GenerationService {
    
    Mono<GenerationResponse> processGenerationRequest(FeGenerationRequest feGenerationRequest);
}

