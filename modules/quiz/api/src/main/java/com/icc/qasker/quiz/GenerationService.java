package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequestt.GenerationRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationService {

    void triggerGeneration(
        String userId,
        GenerationRequest request
    );

    SseEmitter subscribe(String sessionId, String lastEventId);
}

