package com.icc.qasker.oci.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 파일 업로드 검증 설정 */
@ConfigurationProperties(prefix = "q-asker.file-validation")
public record FileValidationProperties(
    long maxFileSize, int maxFileNameLength, String allowedExtensions) {}
