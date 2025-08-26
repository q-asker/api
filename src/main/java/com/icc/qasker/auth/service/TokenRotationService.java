package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.AccessTokenGenerator;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.RefreshTokenGenerator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenGenerator accessTokenGenerator;

    // 새로운 AT, RT 발급
    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenGenerator.issue(userId);
        String newAt = accessTokenGenerator.validateAndGenerate(userId);

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.addHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtPlain).toString());
    }

    // 기존 RT 회전, 새로운 AT, RT 발급
    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenGenerator.validateAndRotate(refreshToken);
        String newAt = accessTokenGenerator.validateAndGenerate(newRtCookie.userId());

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.addHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
        return newAt;
    }
}
