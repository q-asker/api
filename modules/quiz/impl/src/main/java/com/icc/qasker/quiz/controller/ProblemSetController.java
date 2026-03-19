package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.ProblemSetService;
import com.icc.qasker.quiz.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quiz.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ProblemSet", description = "문제세트 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/problem-set")
public class ProblemSetController {

  private final ProblemSetService problemSetService;

  @Operation(summary = "문제세트를 가져온다")
  @GetMapping("/{id}")
  public ResponseEntity<ProblemSetResponse> getProblemSet(@PathVariable("id") String problemSetId) {
    return ResponseEntity.ok(problemSetService.getProblemSet(problemSetId));
  }

  @Operation(summary = "문제세트 제목을 변경한다")
  @PatchMapping("/{id}/title")
  public ResponseEntity<ChangeTitleResponse> changeProblemSet(
      @UserId String userId,
      @PathVariable("id") String problemSetId,
      @Valid @RequestBody ChangeTitleRequest request) {
    return ResponseEntity.ok(
        problemSetService.changeProblemSetTitle(userId, problemSetId, request));
  }
}
