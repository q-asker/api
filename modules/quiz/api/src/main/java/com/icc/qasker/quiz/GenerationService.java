package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;

public interface GenerationService {

    /**
         * Process a frontend generation request and produce a generation response for the specified user.
         *
         * @param feGenerationRequest the generation request payload from the frontend
         * @param userId              identifier of the user initiating the request
         * @return                    a GenerationResponse containing the generated content and related metadata
         */
        GenerationResponse processGenerationRequest(FeGenerationRequest feGenerationRequest,
        String userId);
}
