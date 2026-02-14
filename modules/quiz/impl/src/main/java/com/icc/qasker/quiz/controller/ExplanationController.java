package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.feResponse.ExplanationResponse;
import com.icc.qasker.quiz.dto.feResponse.ExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Explanation", description = "설명 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/explanation")
public class ExplanationController {

    private final ExplanationService explanationService;

    @Operation(summary = "설명을 가져온다")
    @GetMapping("/{id}")
    public ResponseEntity<ExplanationResponse> getExplanation(
        @PathVariable("id") String problemSetId) {
        return ResponseEntity.ok(explanationService.getExplanationByProblemSetId(problemSetId));
    }
}
