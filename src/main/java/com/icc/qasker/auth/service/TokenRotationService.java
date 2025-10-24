package com.icc.qasker.auth.service;

import com.icc.qasker.auth.component.AccessTokenHandler;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.RefreshTokenUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenUtils refreshTokenUtils;
    private final AccessTokenHandler accessTokenHandler;

    public void issueRefreshToken(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtils.issue(userId);
        setRefreshToken(response, newRtPlain);
    }

    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtils.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        setAccessToken(response, newAt);
        setRefreshToken(response, newRtPlain);
    }

    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenUtils.validateAndRotate(refreshToken);
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
