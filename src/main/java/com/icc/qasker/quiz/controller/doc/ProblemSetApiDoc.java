package com.icc.qasker.quiz.controller.doc;

import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "ProblemSet", description = "문제세트 관련 API")
public interface ProblemSetApiDoc {

    @Operation(summary = "문제세트를 가져온다")
    @GetMapping("/{id}")
    ResponseEntity<ProblemSetResponse> getProblemSet(@PathVariable("id") String problemSetId);
}
