package com.icc.qasker.board.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PostRequest(
    @NotBlank(message = "제목이 존재하지 않습니다.") String title,
    @NotBlank(message = "내용이 존재하지 않습니다.") String content) {}
