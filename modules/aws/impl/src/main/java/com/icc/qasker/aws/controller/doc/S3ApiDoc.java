package com.icc.qasker.aws.controller.doc;

import com.icc.qasker.aws.dto.FileExistStatusResponse;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "S3", description = "S3 관련 API")
public interface S3ApiDoc {

    @Operation(summary = "프리사인드 URL을 얻는다")
    @PostMapping("/request-presign")
    ResponseEntity<PresignResponse> upload(
        @Valid
        @RequestBody
        PresignRequest presignRequest
    );

    @Operation(summary = "파일이 존재하는지 확인한다")
    @GetMapping("/check-file-exist")
    ResponseEntity<FileExistStatusResponse> checkFileExist(
        @RequestParam("url") String url
    );
}
