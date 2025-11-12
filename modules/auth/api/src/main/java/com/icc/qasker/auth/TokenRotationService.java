package com.icc.qasker.auth;

import jakarta.servlet.http.HttpServletResponse;

public interface TokenRotationService {

    void issueRefreshToken(String userId, HttpServletResponse response);

    void issueTokens(String userId, HttpServletResponse response);

    String rotateTokens(String refreshToken, HttpServletResponse response);
}

