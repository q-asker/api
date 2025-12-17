package com.icc.qasker.quiz.adapter;

import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.SocketTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException.TooManyRequests;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class AiServerAdapter {

    private final RestClient aiRestClient;

    public AiServerAdapter(
        @Qualifier("aiGenerationRestClient")
        RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    @CircuitBreaker(name = "aiServer")
    public AiGenerationResponse requestGenerate(FeGenerationRequest feGenerationRequest) {
        try {
            return aiRestClient.post()
                .uri("/generation")
                .body(feGenerationRequest)
                .retrieve()
                .body(AiGenerationResponse.class);
        } catch (TooManyRequests e) {
            throw new ClientSideException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
            }
            throw new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        }
    }
}
