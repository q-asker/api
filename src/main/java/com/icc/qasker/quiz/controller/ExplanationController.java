package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.response.ExplanationResponse;
import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.quiz.service.ExplanationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/explanation")
public class ExplanationController {
    private final ExplanationService explanationService;

    @GetMapping("/{id}")
    public ResponseEntity<ExplanationResponse> getExplanation(@PathVariable("id") Long problemSetId) {
        List<ResultResponse> results = explanationService.getExplanationByProblemSetId(problemSetId);
        return ResponseEntity.ok(new ExplanationResponse(results));
    }
}
