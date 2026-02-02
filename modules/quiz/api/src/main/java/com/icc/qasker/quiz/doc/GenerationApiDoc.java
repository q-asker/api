package com.icc.qasker.quiz.doc;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "문제 생성 API")
public interface GenerationApiDoc {

    @Operation(summary = "문제를 전송 받기 위한 세션을 생성하고 키를 발급한다")
    @PostMapping
    GenerationSessionResponse postProblemSetId(
        @UserId
        String userId,
        @RequestBody
        GenerationRequest generationRequest
    );

    @Operation(summary = "세션 키로 세션에 연결한다")
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribeToGeneration(
        @PathVariable
        String sessionId
    );
}
