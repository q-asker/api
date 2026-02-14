package com.icc.qasker.quiz.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.mapper.AIProblemSetMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@AllArgsConstructor
public class AIServerAdapter {

    private final QuizOrchestrationService quizOrchestrationService;

    @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
    public void streamRequest(
        GenerationRequestToAI request,
        Consumer<ProblemSetGeneratedEvent> onLineReceived
    ) {
        quizOrchestrationService.generateQuiz(
            request.uploadedUrl(),
            request.quizType().name(),
            request.quizCount(),
            request.pageNumbers(),
            (problemSet) -> {
                ProblemSetGeneratedEvent event = AIProblemSetMapper.toEvent(problemSet);
                onLineReceived.accept(event);
            }
        );
    }

    private void fallback(GenerationRequestToAI request,
        Consumer<ProblemSetGeneratedEvent> onLineReceived,
        Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.error("⛔ [CircuitBreaker] AI 서버 요청 차단됨 (Circuit Open): {}", t.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }
        if (t instanceof ResourceAccessException e) {
            log.error("⏳ AI 서버 연결 시간 초과/실패: {}", t.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }
        if (t instanceof ClientSideException e) {
            log.error("⏳ 사용자 오류 발생: {}", t.getMessage());
            return;
        }
        log.error("⚠ AI Server Unknown Error: {}", t.getMessage());
        throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
    }
}