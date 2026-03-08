package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;

public interface GenerationCommandService {

    void triggerGeneration(
        String userId,
        GenerationRequest request
    );
}

