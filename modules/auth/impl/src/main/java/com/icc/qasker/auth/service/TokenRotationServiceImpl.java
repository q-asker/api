package com.icc.qasker.auth.service;

import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.component.AccessTokenHandler;
import com.icc.qasker.auth.dto.response.RotateTokenResponse;
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

    /**
     * Issues a new refresh token for the specified user and sets it on the HTTP response.
     *
     * @param userId the identifier of the user for whom to issue the refresh token
     * @param response the HTTP response where the refresh token cookie/header will be written
     */
    @Override
    public void issueRefreshToken(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        setRefreshToken(response, newRtPlain);
    }

    /**
     * Issue a new access token and a new refresh token for the specified user, and persist the refresh token to the HTTP response.
     *
     * @param userId the identifier of the user to issue tokens for
     * @return a RotateTokenResponse containing the newly issued access token
     */
    @Override
    public RotateTokenResponse issueTokens(String userId, HttpServletResponse response) {
        String newRtPlain = refreshTokenUtil.issue(userId);
        String newAt = accessTokenHandler.validateAndGenerate(userId);

        setRefreshToken(response, newRtPlain);
        return new RotateTokenResponse(newAt);
    }

    /**
     * Rotate the provided refresh token, persist the rotated refresh token to the response as a cookie, and generate a new access token.
     *
     * @param refreshToken the refresh token to validate and rotate
     * @param response the HTTP response to which the new refresh-token cookie will be added
     * @return a RotateTokenResponse containing the newly generated access token
     */
    @Override
    public RotateTokenResponse rotateTokens(String refreshToken, HttpServletResponse response) {
        var newRtCookie = refreshTokenUtil.validateAndRotate(refreshToken);
        String newAt = accessTokenHandler.validateAndGenerate(newRtCookie.userId());

        setRefreshToken(response, newRtCookie.newRtPlain());
        return new RotateTokenResponse(newAt);
    }

    /**
     * Stores the provided refresh token in the response's Set-Cookie header.
     *
     * Builds the cookie string for the new refresh token and sets it on the given HTTP response.
     *
     * @param newRtPlain the plaintext refresh token to persist in cookies
     */
    private void setRefreshToken(HttpServletResponse response, String newRtPlain) {
        response.setHeader(HttpHeaders.SET_COOKIE,
            CookieUtil.buildCookies(newRtPlain).toString());
    }
}
