package com.slb.qasker.aws.controller;

import com.slb.qasker.aws.dto.S3UploadRequest;
import com.slb.qasker.aws.dto.S3UploadResponse;
import com.slb.qasker.aws.service.S3Service;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/s3")
@AllArgsConstructor
public class S3Controller {

    public final S3Service s3Service;

    @PostMapping("/upload")
    public ResponseEntity<S3UploadResponse> upload(
        @ModelAttribute
        S3UploadRequest s3UploadRequest
    ) {
        return ResponseEntity.ok(s3Service.uploadFile(s3UploadRequest));
    }
}
