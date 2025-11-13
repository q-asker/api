package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.controller.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.service.MockGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final MockGenerationService mockGenerationService;


    @Value("${spring.datasource.password}")
    private String errorMessagePassword;

    private CustomException customException;

    @PostMapping("/toggle-aws-error")
    public void setAwsError(String password, String message) {
        if (!password.equals(errorMessagePassword)) {
            return;
        }

        if (message.equals("clear")) {
            customException = null;
            return;
        }

        customException = new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    @PostMapping
    public Mono<GenerationResponse> postProblemSetId(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        if (customException != null) {
            throw customException;
        }
        return generationService.processGenerationRequest(feGenerationRequest);
    }

    @PostMapping("/mock")
    public Mono<GenerationResponse> generateMockQuiz(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return mockGenerationService.processGenerationRequest(feGenerationRequest);
    }
}
