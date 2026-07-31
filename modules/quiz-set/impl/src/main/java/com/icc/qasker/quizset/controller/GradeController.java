package com.icc.qasker.quizset.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizset.dto.ferequest.GradeRequest;
import com.icc.qasker.quizset.dto.feresponse.GradeResponse;
import com.icc.qasker.quizset.service.grade.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REAL_BLANK 무상태 채점 API. 공개(인증 불필요)이며 저장하지 않는다 — 결과·해설 화면이 로그인 여부와 무관하게 동일 판정을 얻는 단일 채점
 * 경로다(FR-006).
 */
@Tag(name = "Grade", description = "REAL_BLANK 무상태 채점 API")
@RestController
@RequiredArgsConstructor
public class GradeController {

  private final GradeService gradeService;

  @Operation(summary = "REAL_BLANK 답안을 채점한다 (무상태·공개)")
  @RateLimit(RateLimitTier.READ)
  @PostMapping("/grade")
  public ResponseEntity<GradeResponse> grade(@Valid @RequestBody GradeRequest request) {
    return ResponseEntity.ok(gradeService.grade(request));
  }
}
