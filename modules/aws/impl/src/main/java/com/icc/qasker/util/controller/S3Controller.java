package com.icc.qasker.util.controller;

import com.icc.qasker.util.S3Service;
import com.icc.qasker.util.doc.S3ApiDoc;
import com.icc.qasker.util.dto.FileExistStatusResponse;
import com.icc.qasker.util.dto.PresignRequest;
import com.icc.qasker.util.dto.PresignResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/s3")
@AllArgsConstructor
public class S3Controller implements S3ApiDoc {

    public final S3Service s3Service;

    @GetMapping("/check-file-exist")
    public ResponseEntity<FileExistStatusResponse> checkFileExist(
        @RequestParam("url") String url
    ) {
        return ResponseEntity.ok(s3Service.checkFileExistence(url));
    }

    @PostMapping("/request-presign")
    public ResponseEntity<PresignResponse> upload(
        @Valid
        @RequestBody
        PresignRequest presignRequest
    ) {
        return ResponseEntity.ok(s3Service.requestPresign(presignRequest));
    }
}
