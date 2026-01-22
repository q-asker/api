package com.icc.qasker.auth.component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.JwtProperties;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenHandler {

    private final UserRepository userRepository;

    public String validateAndGenerate(String userId) {
        return userRepository.findById(userId)
            .map(user -> JWT.create()
                .withSubject(user.getUserId())
                .withClaim("userId", user.getUserId())
                .withClaim("nickname", user.getNickname())
                .withExpiresAt(
                    new Date(System.currentTimeMillis() + JwtProperties.ACCESS_EXPIRATION_TIME))
                .sign(Algorithm.HMAC512(JwtProperties.SECRET)))
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));
    }
}
