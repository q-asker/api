package com.icc.qasker.quiz.controller;

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

    @PostMapping
    @Override
    public ResponseEntity<GenerationResponse> postProblemSetId(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return ResponseEntity.ok(generationService.processGenerationRequest(feGenerationRequest));
    }

    @PostMapping("/mock")
    @Override
    public ResponseEntity<GenerationResponse> generateMockQuiz(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return ResponseEntity.ok(
            mockGenerationService.processGenerationRequest(feGenerationRequest));
    }
}
