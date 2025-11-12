package com.icc.qasker.quiz.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.net.HttpURLConnection;
import java.net.URL;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final SlackNotifier slackNotifier;
    private final RestClient aiRestClient;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final S3ValidateService s3ValidateService;


    @Override
    public GenerationResponse processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        try {
            validateQuizCount(feGenerationRequest);
            s3ValidateService.isCloudFrontUrl(feGenerationRequest.uploadedUrl());

            AiGenerationResponse aiResponse = callAiServer(feGenerationRequest);

            ProblemSet problemSet = ProblemSet.of(aiResponse);
            ProblemSet savedPs = problemSetRepository.save(problemSet);

            GenerationResponse response = new GenerationResponse(
                hashUtil.encode(savedPs.getId())
            );

            slackNotifier.notifyText("""
                ✅ [퀴즈 생성 완료 알림]
                ProblemSet ID: %s
                """.formatted(
                response.getProblemSetId()
            ));
            
            return response;
        } catch (Throwable error) {
            throw unifyError(error);
        }
    }

    private void validateQuizCount(FeGenerationRequest feGenerationRequest) {
        if (feGenerationRequest.quizCount() % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }
    }

    private void validate(FeGenerationRequest feGenerationRequest) {
        String uploadedUrl = feGenerationRequest.uploadedUrl();

        try {
            URL url = new URL(uploadedUrl);
            URL encodedUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(),
                url.getPath());
            HttpURLConnection connection = (HttpURLConnection) encodedUrl.openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
            }
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
    }

    private RuntimeException unifyError(Throwable error) {
        if (error instanceof CustomException) {
            return (CustomException) error;
        }
        return new CustomException(ExceptionMessage.DEFAULT_ERROR);
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

