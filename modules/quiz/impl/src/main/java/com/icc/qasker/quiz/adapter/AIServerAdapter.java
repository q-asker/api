package com.icc.qasker.quiz.adapter;

import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@AllArgsConstructor
public class AIServerAdapter {

    RestClient aiStreamClient;

    @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
    public void streamRequest(GenerationRequest request, Consumer<String> onLineReceived) {
        aiStreamClient.post()
            .uri("/generation")
            .body(request)
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange((req, res) -> {
                if (res.getStatusCode().isError()) {
                    String messageBody = new String(res.getBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                    if (res.getStatusCode().is4xxClientError()) {
                        throw new ClientSideException(messageBody);
                    }
                    if (res.getStatusCode().is5xxServerError()) {
                        throw new CustomException(
                            ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
                    }
                }
                while (true) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(res.getBody(),
                        StandardCharsets.UTF_8));
                    String line = br.readLine();
                    if (line == null) {
                        break;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    onLineReceived.accept(line);
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
        log.error("⚠ AI Server Unknown Error: {}", t.getMessage());
        throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
    }
}
