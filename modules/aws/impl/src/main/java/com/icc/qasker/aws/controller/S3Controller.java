package com.icc.qasker.aws.controller;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "S3", description = "S3 관련 API")
@RestController
@RequestMapping("/s3")
@AllArgsConstructor
public class S3Controller {

  public final S3Service s3Service;

  @Operation(summary = "PDF 파일의 프리사인드 URL을 얻는다 (PDF 전용)")
  @PostMapping("/request-presign")
  public ResponseEntity<PresignResponse> upload(@Valid @RequestBody PresignRequest presignRequest) {
    return ResponseEntity.ok(s3Service.requestPresign(presignRequest));
  }

  @Operation(summary = "비PDF 파일을 업로드하여 PDF로 변환한다")
  @PostMapping(value = "/upload-non-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PresignResponse> uploadNonPdf(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(s3Service.uploadNonPdfFile(file));
  }
}
