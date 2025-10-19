package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.controller.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.service.GenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public Mono<GenerationResponse> postProblemSetId(
        @Valid @RequestBody FeGenerationRequest feGenerationRequest) {
        return generationService.processGenerationRequest(feGenerationRequest);
    }

}
