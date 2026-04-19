package com.icc.qasker.quizmake.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AIServerAdapter {

  private final QuizOrchestrationService quizOrchestrationService;

  /**
   * Gemini API를 통해 퀴즈를 생성한다. 인프라 장애(파일 업로드, 캐시 생성/삭제 실패)만 GeminiInfraException으로 전파되어 서킷브레이커 카운트
   * 대상이 된다. 개별 청크 처리 실패는 오케스트레이터 내부에서 흡수된다.
   *
   * @return 이 요청에 적용된 최대 청크 수 (max_chunks)
   */
  @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
  public int streamRequest(GenerationRequestToAI request) {
    return quizOrchestrationService.generateQuiz(request);
  }

  @SuppressWarnings("unused")
  private int fallback(GenerationRequestToAI request, Throwable t) {
    if (t instanceof GeminiInfraException) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, t.getCause().getMessage(), t);
    }
    throw new CustomException(
        ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI Server Unknown Error", t);
  }
}
