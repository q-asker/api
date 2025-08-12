package com.icc.qasker.auth.token;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.quiz.repository.UserRepository;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    private String secretKey;

    public void init() {

    }

    public String createAccessToken(String username, String role) {
        String accessToken = JWT.create()
            .withSubject(username)
            .withExpiresAt(new Date(
                    System.currentTimeMillis() + jwtProperties.getAccessExpirationTime()))
            .withClaim("username", username)
            .withClaim("role", role)
            .sign(Algorithm.HMAC512(jwtProperties.getSecret()));
        return accessToken;
    }
    public String
}
