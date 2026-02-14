package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GenerationRequestToAI;

public interface QuizOrchestrationService {

    void generateQuiz(GenerationRequestToAI request);
}
