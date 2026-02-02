package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationService {

    GenerationSessionResponse triggerGeneration(
        String useId,
        GenerationRequest request
    );

    SseEmitter subscribe(String encodedId);
}

