package com.icc.qasker.quiz.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.mapper.AIProblemSetMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
@AllArgsConstructor
public class AIServerAdapter {

  private final QuizOrchestrationService quizOrchestrationService;

  @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
  public void streamRequest(
      String fileUrl,
      String strategyValue,
      int quizCount,
      List<Integer> referencedPages,
      Consumer<ProblemSetGeneratedEvent> onQuestionsReceived,
      Consumer<Exception> onChunkError) {
    quizOrchestrationService.generateQuiz(
        GenerationRequestToAI.builder()
            .fileUrl(fileUrl)
            .strategyValue(strategyValue)
            .quizCount(quizCount)
            .referencePages(referencedPages)
            .questionsConsumer(
                problemSet -> {
                  ProblemSetGeneratedEvent event = AIProblemSetMapper.toEvent(problemSet);
                  onQuestionsReceived.accept(event);
                })
            .errorConsumer(onChunkError)
            .build());
  }

  @SuppressWarnings("unused")
  private void fallback(
      String fileUrl,
      String strategyValue,
      int quizCount,
      List<Integer> referencedPages,
      Consumer<ProblemSetGeneratedEvent> onQuestionsReceived,
      Consumer<Exception> onChunkError,
      Throwable t) {
    if (t instanceof CallNotPermittedException) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR,
          "CircuitBreaker: AI 서버 요청 차단됨 (Circuit Open)",
          t);
    }
    if (t instanceof ResourceAccessException) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI 서버 연결 시간 초과/실패", t);
    }
    if (t instanceof ClientSideException) {
      return;
    }
    throw new CustomException(
        ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "AI Server Unknown Error", t);
  }
}
