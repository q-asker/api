package com.icc.qasker.quiz.controller;


import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationCommandService;
import com.icc.qasker.quiz.GenerationQueryService;
import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "문제 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController {

    private final GenerationCommandService generationCommandService;
    private final GenerationQueryService generationQueryService;

    @Operation(summary = "제공받은 세션키로 문제 전송을 위한 emitter를 생성한다")
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToGeneration(
        @PathVariable @UUID
        String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "")
        String lastEventId
    ) {
        return generationQueryService.subscribe(sessionId, lastEventId);
    }

    @Operation(summary = "세션에 문제를 전송한다")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void generateQuiz(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest) {
        generationCommandService.triggerGeneration(userId, generationRequest);
    }
}
