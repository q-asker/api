package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import reactor.core.publisher.Flux;

public interface GenerationService {

    Flux<ProblemSetResponse> processGenerationRequest(GenerationRequest generationRequest,
        String userId);
}

