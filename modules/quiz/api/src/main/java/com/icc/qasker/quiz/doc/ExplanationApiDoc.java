package com.icc.qasker.quiz.doc;

import com.icc.qasker.quiz.dto.feResponse.ExplanationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Explanation", description = "설명 관련 API")
public interface ExplanationApiDoc {

    @Operation(summary = "설명을 가져온다")
    @GetMapping("/{id}")
    ResponseEntity<ExplanationResponse> getExplanation(
        @PathVariable("id") String problemSetId);
}
