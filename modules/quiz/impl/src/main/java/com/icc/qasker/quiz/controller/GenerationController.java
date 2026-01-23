package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.controller.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.service.MockGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController implements GenerationApiDoc {

    private final GenerationService generationService;
    private final MockGenerationService mockGenerationService;

    /**
     * Generate a problem set based on the provided request for the specified user.
     *
     * @param userId               the authenticated user's identifier extracted via the {@code @UserId} annotation
     * @param feGenerationRequest  the generation request payload containing parameters for the problem set
     * @return                     the resulting {@link GenerationResponse} describing the generated content
     */
    @PostMapping
    @Override
    public ResponseEntity<GenerationResponse> postProblemSetId(
        @UserId
        String userId,
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return ResponseEntity.ok(
            generationService.processGenerationRequest(feGenerationRequest, userId));
    }

    /**
     * Generate a mock quiz from the provided generation request.
     *
     * @param feGenerationRequest the generation request payload containing criteria for the mock quiz
     * @return a ResponseEntity containing the generated GenerationResponse
     */
    @PostMapping("/mock")
    @Override
    public ResponseEntity<GenerationResponse> generateMockQuiz(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return ResponseEntity.ok(
            mockGenerationService.processGenerationRequest(feGenerationRequest));
    }
}