package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.request.ExplanationRequest;
import com.icc.qasker.quiz.dto.response.ExplanationResponse;
import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.quiz.service.ExplanationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/explanation")
public class ExplanationController {
    private final ExplanationService explanationService;

    @PostMapping
    public ResponseEntity<ExplanationResponse> postExplanation(@RequestBody ExplanationRequest request) {
        List<ResultResponse> results = explanationService.gradeUserAnswers(request);
        return ResponseEntity.ok(new ExplanationResponse(results));
    }
}
