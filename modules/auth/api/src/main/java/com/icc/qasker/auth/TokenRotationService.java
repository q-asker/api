package com.icc.qasker.auth;

import com.icc.qasker.auth.dto.response.RotateTokenResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface TokenRotationService {

    void issueRefreshToken(String userId, HttpServletResponse response);

    RotateTokenResponse issueTokens(String userId, HttpServletResponse response);

    RotateTokenResponse rotateTokens(String refreshToken, HttpServletResponse response);
}

