package com.icc.qasker.quiz.adapter;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiResponse.ErrorEvent;
import com.icc.qasker.quiz.dto.aiResponse.QuizEvent;
import com.icc.qasker.quiz.dto.aiResponse.StreamEvent;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@AllArgsConstructor
public class AIServerAdapter {

    private final ObjectMapper objectMapper;
    private final RestClient aiStreamClient;

    @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
    public void streamRequest(GenerationRequest request, Consumer<QuizEvent> onLineReceived) {
        aiStreamClient.post()
            .uri("/generation")
            .body(request)
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange((req, res) -> {
                HttpStatusCode status = res.getStatusCode();

                if (status.is4xxClientError()) {
                    try (InputStream is = res.getBody()) {
                        String messageBody = new String(is.readAllBytes(), UTF_8);
                        throw new ClientSideException(messageBody);
                    }
                }

                if (status.is5xxServerError()) {
                    try (InputStream is = res.getBody()) {
                        String messageBody = new String(is.readAllBytes(), UTF_8);
                        throw new RuntimeException(messageBody);
                    }
                }

                try (
                    InputStream is = res.getBody();
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(is, UTF_8))
                ) {
                    while (true) {
                        String line = br.readLine();
                        if (line == null) {
                            break;
                        }
                        if (line.isBlank()) {
                            continue;
                        }
                        StreamEvent event = objectMapper.readValue(line, StreamEvent.class);
                        if (event instanceof ErrorEvent error) {
                            if (400 <= error.getCode() && error.getCode() <= 499) {
                                throw new ClientSideException(error.getMessage());
                            } else {
                                throw new RuntimeException(error.getMessage());
                            }
                        } else if (event instanceof QuizEvent quiz) {
                            onLineReceived.accept(quiz);
                        }
                    }
                } catch (IOException e) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
                }

                return null;
            });
    }

    private void fallback(GenerationRequest request, Consumer<String> onLineReceived, Throwable t) {
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