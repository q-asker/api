package com.icc.qasker.admin.controller;

import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.request.ReplyRequest;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리자 전용 API")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final BoardAdminService boardAdminService;

  @Operation(summary = "업데이트 로그를 작성한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping("/boards/update-logs")
  public ResponseEntity<?> createUpdateLog(
      @RequestBody PostRequest request, @UserId String userId) {
    boardAdminService.createUpdateLog(request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "게시글에 관리자 답변을 단다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping("/boards/{boardId}/replies")
  public ResponseEntity<?> reply(
      @RequestBody ReplyRequest replyRequest, @PathVariable Long boardId, @UserId String userId) {
    boardAdminService.reply(boardId, userId, replyRequest.content());
    return ResponseEntity.ok().build();
  }
}
