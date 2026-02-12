package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "생성 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController {

    private final GenerationService generationService;

    @Operation(summary = "문제를 생성한다")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter postProblemSetId(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest) {
        return generationService.processGenerationRequest(generationRequest, userId);
    }
}
