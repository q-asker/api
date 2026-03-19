package com.icc.qasker.quiz.dto.ferequest;

import jakarta.validation.constraints.Size;

public record ChangeTitleRequest(@Size(max = 100, message = "title은 100자 이하여야합니다.") String title) {}
