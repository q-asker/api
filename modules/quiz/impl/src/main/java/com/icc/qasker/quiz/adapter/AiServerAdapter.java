package com.icc.qasker.quiz.adapter;

import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException.TooManyRequests;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class AiServerAdapter {

    private final RestClient aiRestClient;

    public AiServerAdapter(@Qualifier("aiGenerationRestClient") RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    public AiGenerationResponse requestGenerate(FeGenerationRequest feGenerationRequest) {
        try {
            return aiRestClient.post()
                .uri("/generation")
                .body(feGenerationRequest)
                .retrieve()
                .body(AiGenerationResponse.class);
        } catch (TooManyRequests e) {
            // 429 에러 발생 시 로그 출력
            log.error("AI Server Too Many Requests: {}", e.getResponseBodyAsString());
            throw new ClientSideException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        } catch (RestClientResponseException e) {
            // 그 외 4xx, 5xx 에러 발생 시 상대방 서버 메시지 로깅 (가장 중요)
            log.error("AI Server Error Status: {}, Body: {}", e.getStatusCode(),
                e.getResponseBodyAsString());
            throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
        } catch (ResourceAccessException e) {
            // 네트워크 연결 실패나 타임아웃은 응답 본문이 없음
            log.error("AI Server Connection Error: {}", e.getMessage());

            if (e.getCause() instanceof SocketTimeoutException) {
                throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
            }
            throw new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        }
    }
}