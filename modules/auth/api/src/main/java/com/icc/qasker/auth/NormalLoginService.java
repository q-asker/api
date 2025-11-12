package com.icc.qasker.auth;

import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;

public interface NormalLoginService {

    LoginResponse getNickname(LoginRequest loginRequest);
}

