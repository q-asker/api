package com.icc.qasker.quizmake.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizmake.GenerationCommandService;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Generation", description = "문제 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationCommandController {

  private final GenerationCommandService generationCommandService;

  @Operation(summary = "세션에 문제를 전송한다")
  @RateLimit(value = RateLimitTier.CRITICAL, global = true)
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void generateQuiz(
      @UserId String userId, @Valid @RequestBody GenerationRequest generationRequest) {
    generationCommandService.triggerGeneration(userId, generationRequest);
  }
}
