package com.icc.qasker.quiz.adapter;

import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.SocketTimeoutException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@AllArgsConstructor
public class AiServerAdapter {

    private final RestClient aiRestClient;

    @CircuitBreaker(name = "aiServer")
    public AiGenerationResponse requestGenerate(FeGenerationRequest feGenerationRequest) {
        try {
            return aiRestClient.post()
                .uri("/generation")
                .body(feGenerationRequest)
                .retrieve()
                .body(AiGenerationResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ClientSideException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
            }
            throw new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        }
    }
}
