package com.icc.qasker.auth.utils;

import com.icc.qasker.auth.properties.JwtProperties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenUtils {

    private final StringRedisTemplate redis;
    private final RtKeys rtKeys;

    public String issue(String userId) {
        try {
            String rtPlain = TokenUtils.randomUrlSafe(64);
            String rtHash = TokenUtils.sha256Hex(rtPlain);
            String setKey = rtKeys.userSet(userId);

            redis.opsForHash().put(rtHash, "userId", userId);
            redis.opsForSet().add(setKey, rtHash);
            redis.expire(rtHash, rtKeys.ttl());
            redis.expire(setKey, rtKeys.ttl());

            return rtPlain;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }
    }

    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        String userId = (String) redis.opsForHash().get(oldRtHash, "userId");

        if (userId == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        redis.delete(oldRtHash);
        redis.opsForSet().remove(rtKeys.userSet(userId), oldRtHash);

        String newRtPlain = issue(userId);
        return new RotateResult(userId, newRtPlain);
    }

    public void revoke(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        String userId = (String) redis.opsForHash().get(rtHash, "userId");
        if (userId == null) {
            return;
        }
        redis.delete(rtHash);
        redis.opsForSet().remove(rtKeys.userSet(userId), rtHash);
    }

    public record RotateResult(String userId, String newRtPlain) {

    }

    @Component
    public static class RtKeys {

        private final JwtProperties jwtProperties;

        public RtKeys(JwtProperties jwtProperties) {
            this.jwtProperties = jwtProperties;
        }

        public String userSet(String userId) {
            return "rt:u:" + userId;
        }

        public Duration ttl() {
            return Duration.ofSeconds(jwtProperties.getRefreshExpirationTime());
        }
    }

    public static class TokenUtils {

        private static final SecureRandom RAND = new SecureRandom();

        public static String randomUrlSafe(int bytes) {
            byte[] buf = new byte[bytes];
            RAND.nextBytes(buf);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        }

        public static String sha256Hex(String v) {
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
}
