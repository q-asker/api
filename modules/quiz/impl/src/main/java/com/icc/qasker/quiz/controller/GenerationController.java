package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController implements GenerationApiDoc {

    private final GenerationService generationService;

    @Override
    @GetMapping(value = "/{sessionID}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToGeneration(
        @PathVariable @UUID
        String sessionID,
        @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "")
        String lastEventID
    ) {
        return generationService.subscribe(sessionID, lastEventID);
    }

    @Override
    public void generateQuiz(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest) {
        generationService.triggerGeneration(userId, generationRequest);
    }
}
