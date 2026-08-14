package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.strategy.QuizType;

/** 퀴즈 타입별 오케스트레이터 라우팅 인터페이스 */
public interface QuizTypeOrchestrator {

  QuizType getSupportedType();

  void generateQuiz(GenerationRequestToAI request);
}
