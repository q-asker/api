package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class MockGenerationService {

    private static final int DUMMY_PROBLEM_SET_ID = 1;
    private final RestClient aiRestClient;
    private final HashUtil hashUtil;

    public MockGenerationService(
        HashUtil hashUtil,
        @Qualifier("aiMockingRestClient") RestClient aiRestClient1) {
        this.hashUtil = hashUtil;
        this.aiRestClient = aiRestClient1;
    }

    public GenerationResponse processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        AiGenerationResponse aiResponse = callAiServer(feGenerationRequest);

        ProblemSet.of(aiResponse);

        return new GenerationResponse(
            hashUtil.encode(DUMMY_PROBLEM_SET_ID)
        );
    }


    private AiGenerationResponse callAiServer(FeGenerationRequest feGenerationRequest) {
        try {
            return aiRestClient.post()
                .uri("/generation")
                .body(feGenerationRequest)
                .retrieve()
                .body(AiGenerationResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new CustomException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
            }
            throw new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
        }
    }
}
