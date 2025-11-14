package com.icc.qasker.quiz.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AiServerAdapter;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final SlackNotifier slackNotifier;
    private final AiServerAdapter aiServerAdapter;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final S3ValidateService s3ValidateService;

    @Override
    public GenerationResponse processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        try {
            validateQuizCount(feGenerationRequest);
            s3ValidateService.validateS3Bucket(feGenerationRequest.uploadedUrl());
            s3ValidateService.checkCloudFrontUrlWithThrowing(feGenerationRequest.uploadedUrl());

            AiGenerationResponse aiResponse = aiServerAdapter.requestGenerate(feGenerationRequest);

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

    RuntimeException unifyError(Throwable error) {
        if (error instanceof CustomException) {
            return (CustomException) error;
        }
        return new CustomException(ExceptionMessage.DEFAULT_ERROR);
    }
}

