package com.icc.qasker.quiz.service;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.dto.FileExistStatusResponse;
import com.icc.qasker.aws.dto.Status;
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
    private final S3Service s3Service;

    /**
     * Handles a quiz generation request: validates the request and uploaded URL, requests quiz data from the AI server,
     * persists the resulting ProblemSet associated with the given user, and returns the encoded ProblemSet identifier.
     *
     * @param feGenerationRequest the front-end generation request containing quiz parameters and uploaded URL
     * @param userId              the identifier of the user who initiated the generation (associated with the saved ProblemSet)
     * @return                    a GenerationResponse containing the encoded ID of the created ProblemSet
     * @throws CustomException    if the requested quiz count is not a multiple of five, if the uploaded file is not found on S3,
     *                            or if the CloudFront URL validation fails
     */
    @Override
    public GenerationResponse processGenerationRequest(
        FeGenerationRequest feGenerationRequest, String userId) {
        validateQuizCount(feGenerationRequest);
        FileExistStatusResponse fileExistStatusResponse = s3Service.checkFileExistence(
            feGenerationRequest.uploadedUrl());
        if (fileExistStatusResponse.status().equals(Status.NOT_EXIST)) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
        s3ValidateService.checkCloudFrontUrlWithThrowing(feGenerationRequest.uploadedUrl());

        AiGenerationResponse aiResponse = aiServerAdapter.requestGenerate(feGenerationRequest);

        ProblemSet problemSet = ProblemSet.of(aiResponse, userId);
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
    }

    private void validateQuizCount(FeGenerationRequest feGenerationRequest) {
        if (feGenerationRequest.quizCount() % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }
    }
}