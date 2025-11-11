package com.icc.qasker.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class LoginRequest {

    @NotNull(message = "userId가 null입니다.")
    private String userId;
    @NotNull(message = "password가 null입니다.")
    private String password;
}
