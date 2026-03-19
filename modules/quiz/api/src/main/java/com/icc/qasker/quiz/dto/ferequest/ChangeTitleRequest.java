package com.icc.qasker.quiz.dto.ferequest;

import jakarta.validation.constraints.Max;

public record ChangeTitleRequest(
    @Max(value = 100, message = "title은 100자 이하여야합니다.") String title) {}
