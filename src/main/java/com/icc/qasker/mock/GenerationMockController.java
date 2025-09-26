package com.icc.qasker.mock;

import com.icc.qasker.quiz.dto.response.GenerationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generationMock")
public class GenerationMockController {

    private final GenerationMockService generationService;

    @PostMapping
    public Mono<GenerationResponse> postProblemSetId(
        @Valid @RequestBody FeGenerationMockRequest feGenerationRequest) {
        return generationService.processGenerationRequest(feGenerationRequest);
    }
}
