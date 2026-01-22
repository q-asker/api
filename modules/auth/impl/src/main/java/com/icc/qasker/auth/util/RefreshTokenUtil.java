package com.icc.qasker.auth.util;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.properties.JwtProperties;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenUtil {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public String issue(String userId) {
        try {
            String rtPlain = TokenUtils.randomUrlSafe(64);
            String rtHash = TokenUtils.sha256Hex(rtPlain);
            refreshTokenRepository.save(new RefreshToken(userId, rtHash, nextExpiry()));

            return rtPlain;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }
    }

    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        RefreshToken refreshToken = refreshTokenRepository.findByRtHash(oldRtHash)
            .orElseThrow(() -> new CustomException(ExceptionMessage.UNAUTHORIZED));

        if (refreshToken.isExpired(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }

        String newRtPlain = TokenUtils.randomUrlSafe(64);
        String newRtHash = TokenUtils.sha256Hex(newRtPlain);
        refreshToken.rotate(newRtHash, nextExpiry());
        refreshTokenRepository.save(refreshToken);

        return new RotateResult(refreshToken.getUserId(), newRtPlain);
    }

    public void revoke(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        refreshTokenRepository.findByRtHash(rtHash)
            .ifPresent(refreshTokenRepository::delete);
    }

    public record RotateResult(String userId, String newRtPlain) {

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

    private Instant nextExpiry() {
        return Instant.now().plusSeconds(jwtProperties.getRefreshExpirationTime());
    }
}
