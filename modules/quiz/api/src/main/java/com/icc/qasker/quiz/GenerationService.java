package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationService {

    SseEmitter processGenerationRequest(GenerationRequest generationRequest,
        String userId);
}

