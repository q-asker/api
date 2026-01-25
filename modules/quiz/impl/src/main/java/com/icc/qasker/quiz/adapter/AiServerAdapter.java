package com.icc.qasker.quiz.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiResponse.GenerationResponseFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AiServerAdapter {

    private final WebClient webClient;

    public AiServerAdapter(
        @Qualifier("aiStreamClient") WebClient webClient
    ) {
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "aiServer")
    public Flux<GenerationResponseFromAI> requestGenerate(GenerationRequest generationRequest) {
        return webClient.post()
            .uri("/generation")
            .accept(MediaType.APPLICATION_NDJSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(generationRequest)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(
                        errorBody -> {
                            String message = parseErrorMessage(errorBody);
                            log.error("[AI Server] 4xx Error: {}", message);

                            return Mono.error(new ClientSideException(message));
                        }
                    )
            )
            .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(
                        errorBody -> {
                            String message = parseErrorMessage(errorBody);
                            log.error("[AI Server] 5xx Error: {}", message);

                            return Mono.error(new CustomException(
                                ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR));
                        }
                    )
            )
            .bodyToFlux(GenerationResponseFromAI.class)
            .timeout(Duration.ofSeconds(60))
            .doOnError(e -> {
                if (!(e instanceof ClientSideException) && !(e instanceof CustomException)) {
                    log.error("AI Server Network/Timeout Error: {}", e.getMessage());
                }
            });
    }


    private String parseErrorMessage(String messageBody) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(messageBody);
            if (rootNode.has("detail")) {
                return rootNode.get("detail").asText();
            }
        } catch (Exception ignored) {

        }
        return messageBody;
    }
}
