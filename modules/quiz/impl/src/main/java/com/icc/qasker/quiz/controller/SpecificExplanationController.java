package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.SpecificExplanationService;
import com.icc.qasker.quiz.dto.feResponse.SpecificExplanationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SpecificExplanation", description = "상세 설명 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/specific-explanation")
public class SpecificExplanationController {

    private final SpecificExplanationService specificExplanationService;

    @Operation(summary = "상세 설명을 가져온다")
    @GetMapping("/{id}")
    public ResponseEntity<SpecificExplanationResponse> getSpecificExplanation(
        @PathVariable("id") String id,
        @RequestParam("number") int number) {
        return ResponseEntity.ok(specificExplanationService.getSpecificExplanation(id, number));
    }
}
