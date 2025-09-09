package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenHandler refreshTokenHandler;
    private final AccessTokenHandler accessTokenHandler;

    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenHandler.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.setHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtPlain).toString());
    }

    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenHandler.validateAndRotate(refreshToken);
        String newAt = accessTokenHandler.validateAndGenerate(newRtCookie.userId());

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.setHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
        return newAt;
    }
}
