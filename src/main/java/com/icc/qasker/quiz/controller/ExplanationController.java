package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.controller.doc.ExplanationApiDoc;
import com.icc.qasker.quiz.dto.response.ExplanationResponse;
import com.icc.qasker.quiz.service.ExplanationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/explanation")
public class ExplanationController implements ExplanationApiDoc {

    private final ExplanationService explanationService;

    @GetMapping("/{id}")
    public ResponseEntity<ExplanationResponse> getExplanation(
        @PathVariable("id") String problemSetId) {
        return ResponseEntity.ok(explanationService.getExplanationByProblemSetId(problemSetId));
    }
}
