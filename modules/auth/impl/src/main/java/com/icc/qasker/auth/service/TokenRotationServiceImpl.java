package com.icc.qasker.auth.service;

import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.component.AccessTokenHandler;
import com.icc.qasker.auth.dto.response.RotateTokenResponse;
import com.icc.qasker.auth.util.CookieUtil;
import com.icc.qasker.auth.util.RefreshTokenUtil;
import com.icc.qasker.global.properties.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationServiceImpl implements TokenRotationService {

    private final JwtProperties jwtProperties;
    private final RefreshTokenUtil refreshTokenUtil;
    private final AccessTokenHandler accessTokenHandler;

    @Override
    public void issueRefreshToken(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        setRefreshToken(response, newRtPlain);
    }

    @Override
    public RotateTokenResponse issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        setRefreshToken(response, newRtPlain);
        return new RotateTokenResponse(newAt);
    }

    @Override
    public RotateTokenResponse rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenUtil.validateAndRotate(refreshToken);
        String newAt = accessTokenHandler.validateAndGenerate(newRtCookie.userId());

        setRefreshToken(response, newRtCookie.newRtPlain());
        return new RotateTokenResponse(newAt);
    }

    private void setRefreshToken(HttpServletResponse response, String newRtPlain) {
        response.setHeader(HttpHeaders.SET_COOKIE,
            CookieUtil.buildCookies(newRtPlain, jwtProperties.getAccessExpirationTime())
                .toString());
    }
}

