package com.icc.qasker.aws.dto;

public record PresignResponse(
    String uploadUrl,
    String finalUrl,
    boolean isPdf
) {

}
