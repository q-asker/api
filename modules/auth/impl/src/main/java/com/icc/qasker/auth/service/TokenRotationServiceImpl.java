package com.icc.qasker.auth.service;

import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.component.AccessTokenHandler;
import com.icc.qasker.auth.util.CookieUtil;
import com.icc.qasker.auth.util.RefreshTokenUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationServiceImpl implements TokenRotationService {

    private final RefreshTokenUtil refreshTokenUtil;
    private final AccessTokenHandler accessTokenHandler;

    @Override
    public void issueRefreshToken(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        setRefreshToken(response, newRtPlain);
    }

    @Override
    public void issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        setAccessToken(response, newAt);
        setRefreshToken(response, newRtPlain);
    }

    @Override
    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenUtil.validateAndRotate(refreshToken);
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
            CookieUtil.buildCookies(newRtPlain).toString());
    }
}

