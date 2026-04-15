package com.icc.qasker.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameChangeRequest(@NotBlank @Size(max = 20) String nickname) {}
