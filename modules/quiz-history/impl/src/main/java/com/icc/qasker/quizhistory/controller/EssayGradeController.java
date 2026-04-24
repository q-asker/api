package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizhistory.dto.ferequest.EssayGradeRequest;
import com.icc.qasker.quizhistory.dto.feresponse.EssayGradeResponse;
import com.icc.qasker.quizhistory.service.EssayGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ESSAY 채점 API. 문제별 개별 채점을 수행하고 결과를 반환한다. */
@Tag(name = "EssayGrade", description = "서술형 채점 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/essay")
public class EssayGradeController {

  private final EssayGradeService essayGradeService;

  @Operation(summary = "서술형 문제를 채점한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping("/problem-sets/{problemSetId}/problems/{problemNumber}/grade")
  public ResponseEntity<EssayGradeResponse> gradeEssay(
      @UserId String userId,
      @PathVariable String problemSetId,
      @PathVariable int problemNumber,
      @Valid @RequestBody EssayGradeRequest request) {
    EssayGradeResponse response =
        essayGradeService.grade(
            userId, problemSetId, problemNumber, request.textAnswer(), request.attemptCount());
    return ResponseEntity.ok(response);
  }
}
