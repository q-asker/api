package com.icc.qasker.auth;

import com.icc.qasker.auth.dto.response.RotateTokenResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface TokenRotationService {

    /**
 * Issues a refresh token for the specified user and writes it to the provided HTTP response.
 *
 * @param userId   the identifier of the user to issue the refresh token for
 * @param response the HTTP response to which the refresh token will be written
 */
void issueRefreshToken(String userId, HttpServletResponse response);

    /**
 * Issues new access and refresh tokens for the specified user and writes token-related data to the provided HTTP response.
 *
 * @param userId   the identifier of the user for whom tokens are issued
 * @param response the HTTP response to which token data (e.g., headers or cookies) will be written
 * @return a RotateTokenResponse containing the issued access token, refresh token, and associated metadata
 */
RotateTokenResponse issueTokens(String userId, HttpServletResponse response);

    /**
 * Rotate access and refresh tokens using the provided refresh token.
 *
 * Generates new tokens for the user associated with the given refresh token and may write token data (for example headers or cookies) to the HTTP response.
 *
 * @param refreshToken the refresh token used to validate the session and produce new tokens
 * @param response     the HTTP response to which token data may be written
 * @return             a RotateTokenResponse containing the newly issued access token, refresh token, and related metadata
 */
RotateTokenResponse rotateTokens(String refreshToken, HttpServletResponse response);
}
