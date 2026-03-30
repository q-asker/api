package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GenerationRequestToAI;

public interface QuizOrchestrationService {

  /**
   * @return 이 요청에 적용된 최대 청크 수 (max_chunks)
   */
  int generateQuiz(GenerationRequestToAI request);
}
