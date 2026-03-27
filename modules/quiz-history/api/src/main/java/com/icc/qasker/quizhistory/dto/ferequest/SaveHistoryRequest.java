package com.icc.qasker.quizhistory.dto.ferequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveHistoryRequest(
    @NotBlank(message = "problemSetId가 비어있습니다.") String problemSetId,
    String title,
    @NotNull(message = "userAnswers가 null입니다.") List<UserAnswer> userAnswers,
    int score,
    String totalTime) {}
