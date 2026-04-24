package com.icc.qasker.quizhistory.dto.ferequest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EssayGradeRequest(
    @NotBlank(message = "답안이 비어있습니다.") @Size(max = 1000, message = "답안은 1000자를 초과할 수 없습니다.")
        String textAnswer,
    @NotNull(message = "시도 횟수가 비어있습니다.")
        @Min(value = 1, message = "시도 횟수는 1 이상이어야 합니다.")
        @Max(value = 4, message = "시도 횟수는 4 이하여야 합니다.")
        Integer attemptCount) {}
