package com.icc.qasker.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class S3UploadResponse {

    private String uploadedUrl;
}
