package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationService {

    void triggerGeneration(
        String useId,
        GenerationRequest request
    );

    SseEmitter subscribe(String sessionID, String lastEventID);
}

