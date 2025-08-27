package com.icc.qasker.auth.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessTokenGenerator {

    private final UserRepository userRepository;

    public String validateAndGenerate(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("invalid/expired access token"));

        return JWT.create()
            .withSubject(user.getUserId())
            .withClaim("userId", user.getUserId())
            .withClaim("role", user.getRole())
            .withExpiresAt(
                new Date(System.currentTimeMillis() + JwtProperties.ACCESS_EXPIRATION_TIME))
            .sign(Algorithm.HMAC512(JwtProperties.SECRET));
    }


}
