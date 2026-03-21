package com.icc.qasker.quiz.dto.ferequest;

import jakarta.validation.constraints.NotBlank;

public record InitHistoryRequest(
    @NotBlank(message = "problemSetId가 비어있습니다.") String problemSetId,
    @NotBlank(message = "title이 비어있습니다.") String title) {}
