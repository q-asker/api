package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.controller.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.service.GenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController implements GenerationApiDoc {

    private final GenerationService generationService;
    private boolean awsError = false;

    @GetMapping("/toggle-aws-error")
    public void setAwsError() {
        awsError = !awsError;
    }

    @PostMapping
    public Mono<GenerationResponse> postProblemSetId(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        if (awsError) {
            throw new CustomException(ExceptionMessage.AWS_SERVICE_ERROR);
        }
        return generationService.processGenerationRequest(feGenerationRequest);
    }
}
