package com.icc.qasker.aws.controller;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.controller.doc.S3ApiDoc;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/s3")
@AllArgsConstructor
public class S3Controller implements S3ApiDoc {

    public final S3Service s3Service;

    @PostMapping("/request-presign")
    public ResponseEntity<PresignResponse> upload(
        @Valid
        @RequestBody
        PresignRequest presignRequest
    ) {
        return ResponseEntity.ok(s3Service.requestPresign(presignRequest));
    }
}
