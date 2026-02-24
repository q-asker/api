package com.icc.qasker.aws.controller;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.dto.FileExistStatusResponse;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "S3", description = "S3 관련 API")
@RestController
@RequestMapping("/s3")
@AllArgsConstructor
public class S3Controller {

    public final S3Service s3Service;

    @Operation(summary = "파일이 존재하는지 확인한다")
    @GetMapping("/check-file-exist")
    public ResponseEntity<FileExistStatusResponse> checkFileExist(
        @RequestParam("url") String url
    ) {
        return ResponseEntity.ok(s3Service.checkFileExistence(url));
    }

    @Operation(summary = "프리사인드 URL을 얻는다")
    @PostMapping("/request-presign")
    public ResponseEntity<PresignResponse> upload(
        @Valid
        @RequestBody
        PresignRequest presignRequest
    ) {
        return ResponseEntity.ok(s3Service.requestPresign(presignRequest));
    }
}
