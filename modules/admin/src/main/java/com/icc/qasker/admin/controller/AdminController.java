package com.icc.qasker.admin.controller;

import com.icc.qasker.admin.properties.ImageUploadProperties;
import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.request.ReplyRequest;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.oci.ObjectStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin", description = "관리자 전용 API")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final BoardAdminService boardAdminService;
  private final ObjectStorageService objectStorageService;
  private final ImageUploadProperties imageUploadProperties;

  @Operation(summary = "업데이트 로그를 작성한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping("/boards/update-logs")
  public ResponseEntity<?> createUpdateLog(
      @RequestBody PostRequest request, @UserId String userId) {
    boardAdminService.createUpdateLog(request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "업데이트 로그를 수정한다")
  @RateLimit(RateLimitTier.WRITE)
  @PutMapping("/boards/update-logs/{boardId}")
  public ResponseEntity<?> updateUpdateLog(
      @PathVariable Long boardId, @Valid @RequestBody PostRequest request, @UserId String userId) {
    boardAdminService.updateUpdateLog(boardId, request, userId);
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

  @Operation(summary = "업데이트 로그용 이미지를 업로드한다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
    String contentType = validateImage(file);

    try {
      String url =
          objectStorageService.uploadImage(
              file.getInputStream(), file.getSize(), contentType, file.getOriginalFilename());
      return ResponseEntity.ok(Map.of("url", url));
    } catch (IOException e) {
      throw new CustomException(ExceptionMessage.FILE_UPLOAD_FAILED);
    }
  }

  /** 업로드 이미지의 존재·크기·contentType을 검증하고 검증된 contentType을 반환한다. */
  private String validateImage(MultipartFile file) {
    if (file.isEmpty()) {
      throw new CustomException(ExceptionMessage.FILE_NAME_NOT_EXIST);
    }
    if (file.getSize() > imageUploadProperties.maxSize()) {
      throw new CustomException(ExceptionMessage.OUT_OF_FILE_SIZE);
    }
    String contentType = file.getContentType();
    if (contentType == null || !imageUploadProperties.allowedTypes().contains(contentType)) {
      throw new CustomException(ExceptionMessage.EXTENSION_INVALID);
    }
    return contentType;
  }
}
