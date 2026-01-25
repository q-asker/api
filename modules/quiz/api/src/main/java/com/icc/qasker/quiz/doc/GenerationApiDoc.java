package com.icc.qasker.quiz.doc;

import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Flux;

@Tag(name = "Generation", description = "생성 관련 API")
public interface GenerationApiDoc {

    @Operation(summary = "문제를 생성한다")
    @PostMapping
    Flux<ProblemSetResponse> postProblemSetId(
        String userId,
        @RequestBody GenerationRequest generationRequest);
}
