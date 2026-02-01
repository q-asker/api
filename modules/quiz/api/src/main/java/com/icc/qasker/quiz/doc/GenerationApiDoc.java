package com.icc.qasker.quiz.doc;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "생성 관련 API")
public interface GenerationApiDoc {

    @Operation(summary = "문제를 생성한다")
    @PostMapping
    SseEmitter postProblemSetId(
        @UserId
        String userId,
        @RequestBody GenerationRequest generationRequest);
}
