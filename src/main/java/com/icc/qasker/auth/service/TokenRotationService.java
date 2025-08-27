package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;

    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenService.issue(userId);
        String newAt = accessTokenService.validateAndGenerate(userId);

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.addHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtPlain).toString());
    }

    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenService.validateAndRotate(refreshToken);
        String newAt = accessTokenService.validateAndGenerate(newRtCookie.userId());

        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
        response.addHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
        return newAt;
    }
}
