package com.icc.qasker.quiz.service;

import com.icc.qasker.util.S3Service;
import com.icc.qasker.util.S3ValidateService;
import com.icc.qasker.util.dto.FileExistStatusResponse;
import com.icc.qasker.util.dto.Status;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AiServerAdapter;
import com.icc.qasker.quiz.dto.aiResponse.GenerationResponseFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final SlackNotifier slackNotifier;
    private final AiServerAdapter aiServerAdapter;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final S3ValidateService s3ValidateService;
    private final S3Service s3Service;

    @Override
    public Flux<GenerationResponse> processGenerationRequest(
        GenerationRequest request, String userId) {
        validateFile(request.uploadedUrl());

        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
        ProblemSet savedPs = problemSetRepository.save(problemSet);

        Flux<GenerationResponseFromAI> aiResponse = aiServerAdapter.requestGenerate(request);

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

    private void validateFile(String uploadUrl) {
        FileExistStatusResponse fileExistStatusResponse = s3Service.checkFileExistence(uploadUrl);
        if (fileExistStatusResponse.status().equals(Status.NOT_EXIST)) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
        s3ValidateService.checkCloudFrontUrlWithThrowing(uploadUrl);
    }
}