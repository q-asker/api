package com.icc.qasker.quizhistory.dto.ferequest;

import jakarta.validation.constraints.Size;

public record UserAnswer(
    int number,
    int userAnswer,
    boolean inReview,
    @Size(max = 1000, message = "답안은 1000자를 초과할 수 없습니다.") String textAnswer) {}
