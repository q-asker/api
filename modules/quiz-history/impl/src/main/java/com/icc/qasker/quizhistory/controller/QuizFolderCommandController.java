package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizhistory.QuizFolderCommandService;
import com.icc.qasker.quizhistory.dto.ferequest.CreateFolderRequest;
import com.icc.qasker.quizhistory.dto.ferequest.RenameFolderRequest;
import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 퀴즈 기록 폴더 커맨드 API (생성·이름변경·삭제). */
@Tag(name = "QuizFolder", description = "퀴즈 기록 폴더 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/folders")
public class QuizFolderCommandController {

  private final QuizFolderCommandService quizFolderCommandService;

  @Operation(summary = "폴더를 생성한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping
  public ResponseEntity<FolderResponse> createFolder(
      @UserId String userId, @RequestBody CreateFolderRequest request) {
    FolderResponse response = quizFolderCommandService.createFolder(userId, request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "폴더 이름을 변경한다")
  @RateLimit(RateLimitTier.WRITE)
  @PatchMapping("/{folderId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void renameFolder(
      @UserId String userId,
      @PathVariable String folderId,
      @RequestBody RenameFolderRequest request) {
    quizFolderCommandService.renameFolder(userId, folderId, request.name());
  }

  @Operation(summary = "폴더를 삭제한다 (소속 기록은 미분류로 이동)")
  @RateLimit(RateLimitTier.WRITE)
  @DeleteMapping("/{folderId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFolder(@UserId String userId, @PathVariable String folderId) {
    quizFolderCommandService.deleteFolder(userId, folderId);
  }
}
