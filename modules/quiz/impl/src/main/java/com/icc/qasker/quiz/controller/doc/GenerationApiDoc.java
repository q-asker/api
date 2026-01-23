package com.icc.qasker.quiz.controller.doc;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Generation", description = "생성 관련 API")
public interface GenerationApiDoc {

    /**
         * Generate a new problem set for the specified user.
         *
         * @param userId the identifier of the user requesting the generation
         * @param feGenerationRequest the generation request payload containing parameters for the problem set
         * @return a ResponseEntity containing a GenerationResponse with the generated problem set and related metadata
         */
        @Operation(summary = "문제를 생성한다")
    @PostMapping
    ResponseEntity<GenerationResponse> postProblemSetId(
        String userId,
        @RequestBody FeGenerationRequest feGenerationRequest);


    @Operation(summary = "모의 퀴즈 생성을 요청한다")
    @PostMapping("/mock")
    ResponseEntity<GenerationResponse> generateMockQuiz(
        @RequestBody FeGenerationRequest feGenerationRequest);
}