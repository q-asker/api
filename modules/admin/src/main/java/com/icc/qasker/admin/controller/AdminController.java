package com.icc.qasker.admin.controller;

import com.icc.qasker.admin.properties.ImageUploadProperties;
import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.request.ReplyRequest;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.quizset.ExplanationReviewService;
import com.icc.qasker.quizset.QualityReviewService;
import com.icc.qasker.quizset.dto.ExplanationReviewResult;
import com.icc.qasker.quizset.dto.QualityReviewResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final QualityReviewService qualityReviewService;
  private final ExplanationReviewService explanationReviewService;

  @Operation(summary = "여러 세트 품질 재검토를 일괄 요청한다(Pass 2, 비동기)")
  @PostMapping("/problem-sets/quality-review")
  public ResponseEntity<?> requestQualityReviewBulk(@RequestBody Map<String, List<Long>> body) {
    List<Long> setIds = body.getOrDefault("setIds", List.of());
    qualityReviewService.submitReviewBulk(setIds);
    return ResponseEntity.accepted().build();
  }

  @Operation(summary = "세트 품질 재검토 결과를 조회한다")
  @GetMapping("/problem-sets/{setId}/quality-review")
  public ResponseEntity<QualityReviewResult> getQualityReviewResult(@PathVariable Long setId) {
    return qualityReviewService
        .latestResult(setId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
  }

  @Operation(summary = "여러 세트 해설 형식 검증(정규식)을 일괄 수행한다")
  @PostMapping("/problem-sets/explanation-review")
  public ResponseEntity<List<ExplanationReviewResult>> requestExplanationReview(
      @RequestBody Map<String, List<Long>> body) {
    List<Long> setIds = body.getOrDefault("setIds", List.of());
    return ResponseEntity.ok(setIds.stream().map(explanationReviewService::review).toList());
  }

  @Operation(summary = "업데이트 로그를 작성한다")
  @PostMapping("/boards/update-logs")
  public ResponseEntity<?> createUpdateLog(
      @RequestBody PostRequest request, @UserId String userId) {
    boardAdminService.createUpdateLog(request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "업데이트 로그를 수정한다")
  @PutMapping("/boards/update-logs/{boardId}")
  public ResponseEntity<?> updateUpdateLog(
      @PathVariable Long boardId, @Valid @RequestBody PostRequest request, @UserId String userId) {
    boardAdminService.updateUpdateLog(boardId, request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "게시글에 관리자 답변을 단다")
  @PostMapping("/boards/{boardId}/replies")
  public ResponseEntity<?> reply(
      @RequestBody ReplyRequest replyRequest, @PathVariable Long boardId, @UserId String userId) {
    boardAdminService.reply(boardId, userId, replyRequest.content());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "업데이트 로그용 이미지를 업로드한다")
  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
    try {
      String url =
          objectStorageService.uploadImage(
              file.getInputStream(),
              file.getSize(),
              file.getContentType(),
              file.getOriginalFilename());
      return ResponseEntity.ok(Map.of("url", url));
    } catch (IOException e) {
      throw new CustomException(ExceptionMessage.FILE_UPLOAD_FAILED);
    }
  }
}
