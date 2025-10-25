package com.icc.qasker.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class LoginResponse {

    private String nickname;
}
