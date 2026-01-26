package com.icc.qasker.util.dto;

public record PresignResponse(
    String uploadUrl,
    String finalUrl,
    boolean isPdf
) {

}
