package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.ProblemSetService;
import com.icc.qasker.quiz.controller.doc.ProblemSetApiDoc;
import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/problem-set")
public class ProblemSetController implements ProblemSetApiDoc {

    private final ProblemSetService problemSetService;

    @GetMapping("/{id}")
    public ResponseEntity<ProblemSetResponse> getProblemSet(
        @PathVariable("id") String problemSetId) {
        return ResponseEntity.ok(problemSetService.getProblemSet(problemSetId));
    }
}