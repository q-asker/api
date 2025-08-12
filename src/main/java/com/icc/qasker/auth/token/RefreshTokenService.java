package com.icc.qasker.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final StringRedisTemplate redis;
    private static final SecureRandom RAND = new SecureRandom();


    // 발급
    public String createRefreshToken(Long userId) {
        String rtPlain = randomUrlSafe(64);
        String rtHash = sha256Hex(rtPlain);

        repo.save(new RefreshToken(userId, rtHash));

        String setKey = "rt:u: " + userId;
        redis.opsForSet().add(setKey, rtHash);

        redis.expire(setKey, Duration.ofDays(7));

        return rtPlain;
    }

    static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RAND.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String sha256Hex(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
