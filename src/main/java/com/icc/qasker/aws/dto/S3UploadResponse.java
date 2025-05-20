package com.icc.qasker.aws.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class S3UploadResponse {

    private String uploadedUrl;
}
