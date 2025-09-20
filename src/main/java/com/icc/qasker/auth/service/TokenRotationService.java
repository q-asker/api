package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.AccessTokenHandler;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.RefreshTokenHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenHandler refreshTokenHandler;
    private final AccessTokenHandler accessTokenHandler;

    public void issueRefreshToken(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenHandler.issue(userId);
        setRefreshToken(response, newRtPlain);
    }

    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenHandler.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        setAccessToken(response, newAt);
        setRefreshToken(response, newRtPlain);
    }

    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenHandler.validateAndRotate(refreshToken);
        String newAt = accessTokenHandler.validateAndGenerate(newRtCookie.userId());

        setAccessToken(response, newAt);
        setRefreshToken(response, newRtCookie.newRtPlain());
        return newAt;
    }

    private void setAccessToken(HttpServletResponse response, String newAt) {
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
    }

    private void setRefreshToken(HttpServletResponse response, String newRtPlain) {
        response.setHeader(HttpHeaders.SET_COOKIE,
            CookieUtils.buildCookies(newRtPlain).toString());
    }
}
