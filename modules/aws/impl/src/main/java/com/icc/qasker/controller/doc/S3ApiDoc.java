package com.icc.qasker.controller.doc;

import com.icc.qasker.dto.S3UploadRequest;
import com.icc.qasker.dto.S3UploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "S3", description = "S3 관련 API")
public interface S3ApiDoc {

    @Operation(summary = "S3에 파일을 업로드한다")
    @PostMapping("/upload")
    ResponseEntity<S3UploadResponse> upload(
        @ModelAttribute
        S3UploadRequest s3UploadRequest
    );
}
