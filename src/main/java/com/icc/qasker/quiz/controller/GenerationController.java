package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import com.icc.qasker.quiz.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    public Mono<ProblemSetResponse> postQuiz(@RequestBody FeGenerationRequest feGenerationRequest) {
        return generationService.processGenerationRequest(feGenerationRequest);
    }

}
