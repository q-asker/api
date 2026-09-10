package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.quiz.QuizType;

public interface QuizTypeOrchestrator {

  QuizType getSupportedType();

  void generateQuiz(GenerationRequestToAI request);
}
