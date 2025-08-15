package com.icc.qasker.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.RefreshTokenRepository.UserRepository;
import com.icc.qasker.auth.utils.JwtProperties;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final UserRepository userRepository;

    public String validateAndGenerate(String userId, String newRtPlain) {
        // 에러 처리 나중에 추가
        User user = userRepository.findById(userId).orElse(null);
        String newAt = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("userId", user.getUserId())
            .withClaim("role", user.getRole())
            .withExpiresAt(
                new Date(System.currentTimeMillis() + JwtProperties.accessExpirationTime))
            .sign(Algorithm.HMAC512(JwtProperties.secret));
        return newAt;
    }

}
