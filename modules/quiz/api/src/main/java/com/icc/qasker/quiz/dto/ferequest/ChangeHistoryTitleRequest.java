package com.icc.qasker.quiz.dto.ferequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeHistoryTitleRequest(
    @NotBlank(message = "제목은 필수입니다.") @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title) {}
