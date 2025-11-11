package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.SpecificExplanationService;
import com.icc.qasker.quiz.controller.doc.SpecificExplanationApiDoc;
import com.icc.qasker.quiz.dto.response.SpecificExplanationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/specific-explanation")
public class SpecificExplanationController implements SpecificExplanationApiDoc {

    private final SpecificExplanationService specificExplanationService;

    @GetMapping("/{id}")
    public ResponseEntity<SpecificExplanationResponse> getSpecificExplanation(
        @PathVariable("id") String id,
        @RequestParam("number") int number) {
        return ResponseEntity.ok(specificExplanationService.getSpecificExplanation(id, number));
    }
}
