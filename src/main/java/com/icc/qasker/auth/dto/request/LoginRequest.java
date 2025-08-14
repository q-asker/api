package com.icc.qasker.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class LoginRequest {

    @NotNull(message = "username가 null입니다.")
    private String username;
    @NotNull(message = "password가 null입니다.")
    private String password;
}
