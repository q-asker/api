package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.AccessTokenGenerator;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.RefreshTokenGenerator;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenGenerator accessTokenGenerator;

    public void issueTokens(String userId, HttpServletResponse response) {
        try {
            String newRtPlain = refreshTokenGenerator.issue(userId);
            String newAt = accessTokenGenerator.validateAndGenerate(userId);

            response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.buildCookies(newRtPlain).toString());
        } catch (IllegalStateException e) {
            throw new CustomException(ExceptionMessage.INVALID_JWT);
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }

    }

    public String rotateTokens(String refreshToken, HttpServletResponse response) {
        try {
            var newRtCookie = refreshTokenGenerator.validateAndRotate(refreshToken);
            String newAt = accessTokenGenerator.validateAndGenerate(newRtCookie.userId());

            response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
            return newAt;
        } catch (IllegalStateException e) {
            throw new CustomException(ExceptionMessage.INVALID_JWT);
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }
    }
}
