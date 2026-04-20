package com.icc.qasker.board.controller;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;
import com.icc.qasker.board.service.FeedbackService;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Board", description = "피드백 관련 API")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackBoardController {

  private final FeedbackService feedbackService;

  @Operation(summary = "피드백을 작성한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping
  public ResponseEntity<Void> postFeedback(
      @UserId String userId, @Valid @RequestBody PostFeedbackRequest request) {
    feedbackService.postFeedback(userId, request);
    return ResponseEntity.accepted().build();
  }
}
