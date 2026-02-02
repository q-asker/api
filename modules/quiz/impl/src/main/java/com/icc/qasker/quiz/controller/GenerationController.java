package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController implements GenerationApiDoc {

    private final GenerationService generationService;

    @Override
    public GenerationSessionResponse postProblemSetId(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest) {
        return generationService.triggerGeneration(userId, generationRequest);
    }

    @Override
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToGeneration(
        @PathVariable
        String sessionId
    ) {
        return generationService.subscribe(sessionId);
    }
}
