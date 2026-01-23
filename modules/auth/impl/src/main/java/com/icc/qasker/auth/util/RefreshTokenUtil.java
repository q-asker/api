package com.icc.qasker.auth.util;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.JwtProperties;
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

    /**
     * Generates and persists a new refresh token for the given user.
     *
     * @param userId the id of the user to associate with the refresh token
     * @return the newly generated plain (unhashed) refresh token string
     * @throws CustomException if token generation or persistence fails
     */
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

    /**
     * Validates a presented refresh token, rotates it if valid, and issues a new refresh token for the same user.
     *
     * @param oldRtPlain the presented plain-text refresh token to validate and rotate
     * @return a RotateResult containing the associated userId and the newly issued plain refresh token
     * @throws CustomException with ExceptionMessage.UNAUTHORIZED if the presented token is not found or is expired
     */
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

    /**
     * Removes the stored refresh token that corresponds to the provided plain token, if present.
     *
     * @param presentedRtPlain the plain (unhashed) refresh token presented by the client
     */
    public void revoke(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        refreshTokenRepository.findByRtHash(rtHash)
            .ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Compute the expiry instant for a new refresh token.
     *
     * @return the Instant representing the current time plus the configured refresh token lifetime (in seconds)
     */
    private Instant nextExpiry() {
        return Instant.now().plusSeconds(jwtProperties.getRefreshExpirationTime());
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
}