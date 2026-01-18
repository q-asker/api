package com.icc.qasker.aws.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignRequest(

    @NotBlank(message = "파일명은 필수입니다.")
    String originalFileName,

    @NotBlank(message = "파일 타입(MIME)은 필수입니다.")
    String contentType,

    @NotNull(message = "파일 크기는 필수입니다.")
    @Positive(message = "파일 크기는 0보다 커야 합니다.")
    long fileSize
) {

}
