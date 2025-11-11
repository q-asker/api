package com.icc.qasker;

import com.icc.qasker.dto.request.LoginRequest;
import com.icc.qasker.dto.response.LoginResponse;

public interface NormalLoginService {
    
    LoginResponse getNickname(LoginRequest loginRequest);
}

