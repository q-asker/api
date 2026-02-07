package com.icc.qasker.quiz.doc;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "문제 생성 API")
public interface GenerationApiDoc {


    @Operation(summary = "제공받은 세션키로 문제 전송을 위한 emitter를 생성한다")
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribeToGeneration(
        @PathVariable @UUID
        String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "")
        String lastEventId
    );

    @Operation(summary = "세션에 문제를 전송한다")
    @PostMapping
    void generateQuiz(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest
    );
}
