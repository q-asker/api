package com.icc.qasker.quiz.adapter;

import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AiServerAdapter {

    private final RestClient aiRestClient;

    public AiServerAdapter(@Qualifier("aiGenerationRestClient") RestClient aiRestClient) {
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

            // 1. 429 에러 -> 서킷 브레이커가 무시해야 함 (ignoreExceptions)
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("AI Server Rate Limit Exceeded: Status={}, Body={}", e.getStatusCode(),
                e.getResponseBodyAsString());
            throw new ClientSideException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);

            // 2. 5xx 에러 (Server Fault) -> 서킷 브레이커가 실패로 기록해야 함
        } catch (HttpServerErrorException e) {
            log.error("AI Server Server Error (5xx): Status={}, Body={}", e.getStatusCode(),
                e.getResponseBodyAsString());
            // 예외를 그대로 던지거나, 커스텀 예외(ServerException)로 감싸서 던져야 함
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);

            // 3. 타임아웃/연결 오류 (Network Fault) -> 서킷 브레이커가 실패로 기록해야 함
        } catch (ResourceAccessException e) {
            log.error("AI Server Connection/Timeout Error: {}", e.getMessage());
            // 타임아웃은 반드시 던져야 함
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);

            // 4. 그 외의 오류
        } catch (Exception e) {
            log.error("AI Server Unknown Error: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }
    }
}