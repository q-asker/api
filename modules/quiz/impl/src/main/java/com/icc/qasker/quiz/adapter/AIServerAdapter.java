package com.icc.qasker.quiz.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
@AllArgsConstructor
public class AIServerAdapter {

  private final QuizOrchestrationService quizOrchestrationService;

  @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
  public void streamRequest(GenerationRequestToAI request) {
    quizOrchestrationService.generateQuiz(request);
  }

  @SuppressWarnings("unused")
  private void fallback(GenerationRequestToAI request, Throwable t) {
    if (t instanceof CallNotPermittedException) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR,
          "CircuitBreaker: AI 서버 요청 차단됨 (Circuit Open)",
          t);
    }
    if (t instanceof ClientSideException) {
      return;
    }
    if (t instanceof ResourceAccessException) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI 서버 연결 시간 초과/실패", t);
    }
    throw new CustomException(
        ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI Server Unknown Error", t);
  }
}
