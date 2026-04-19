package com.icc.qasker.quizmake.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizmake.dto.feresponse.FileUploadResponse;
import com.icc.qasker.quizmake.service.upload.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "File", description = "파일 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class FileUploadController {

  private final FileUploadService fileUploadService;

  @Operation(summary = "파일(PDF, PPT, DOCX)을 PDF로 변환 후 S3와 Gemini에 동시 업로드한다")
  @RateLimit(RateLimitTier.CRITICAL)
  @PostMapping(value = "/upload-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(fileUploadService.upload(file));
  }
}
