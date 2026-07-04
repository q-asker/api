package com.icc.qasker.auth.util;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.JwtProperties;
import jakarta.transaction.Transactional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenUtil {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  public String issue(String userId) {
    try {
      String rtPlain = TokenCrypto.randomUrlSafe(64);
      String rtHash = TokenCrypto.sha256Hex(rtPlain);
      refreshTokenRepository.save(new RefreshToken(userId, rtHash, nextExpiry()));
      return rtPlain;
    } catch (Exception e) {
      throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED, e);
    }
  }

  @Transactional
  public RotateResult validateAndRotate(String oldRtPlain) {
    String oldRtHash = TokenCrypto.sha256Hex(oldRtPlain);

    RefreshToken refreshToken =
        refreshTokenRepository
            .findByRtHash(oldRtHash)
            .orElseThrow(() -> new CustomException(ExceptionMessage.LOGIN_REQUIRED));

    if (refreshToken.isExpired(Instant.now())) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }

    String newRtPlain = TokenCrypto.randomUrlSafe(64);
    String newRtHash = TokenCrypto.sha256Hex(newRtPlain);
    refreshToken.rotate(newRtHash, nextExpiry());
    refreshTokenRepository.save(refreshToken);

    return new RotateResult(refreshToken.getUserId(), newRtPlain);
  }

  @Transactional
  public void revoke(String presentedRtPlain) {
    String rtHash = TokenCrypto.sha256Hex(presentedRtPlain);
    refreshTokenRepository.findByRtHash(rtHash).ifPresent(refreshTokenRepository::delete);
  }

  private Instant nextExpiry() {
    return Instant.now().plusSeconds(jwtProperties.getRefreshExpirationSecond());
  }

  public record RotateResult(String userId, String newRtPlain) {}
}
