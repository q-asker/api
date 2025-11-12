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

    @Operation(summary = "문제를 생성한다")
    @PostMapping
    ResponseEntity<GenerationResponse> postProblemSetId(
        @RequestBody FeGenerationRequest feGenerationRequest);
}
