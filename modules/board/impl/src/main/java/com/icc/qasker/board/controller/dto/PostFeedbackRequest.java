package com.icc.qasker.board.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record PostFeedbackRequest(@NotBlank(message = "피드백이 존재하지 않습니다.") String content) {}
