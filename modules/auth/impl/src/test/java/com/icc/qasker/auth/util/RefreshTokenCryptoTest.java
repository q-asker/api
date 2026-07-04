package com.icc.qasker.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TokenCrypto 회귀 테스트.
 *
 * <p>TokenUtils top-level 분리 리팩터링의 안전망. 해시 알고리즘(SHA-256 hex)·토큰 바이트 길이(64) 상수가 변하면 기존 저장 refresh
 * token이 전부 무효화되므로 이 테스트가 항상 통과해야 한다.
 */
class RefreshTokenCryptoTest {

  @Nested
  @DisplayName("sha256Hex")
  class Sha256Hex {

    @Test
    @DisplayName("알려진 입력에 대해 고정 hex 값을 반환한다")
    void knownInput() {
      String result = TokenCrypto.sha256Hex("hello");
      assertThat(result)
          .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("결과는 64자 소문자 hex 문자열이다")
    void resultIs64LowercaseHex() {
      String result = TokenCrypto.sha256Hex("any-token-value");
      assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("같은 입력은 항상 같은 출력을 낸다 (결정론적)")
    void deterministic() {
      String v = "some-refresh-token";
      assertThat(TokenCrypto.sha256Hex(v)).isEqualTo(TokenCrypto.sha256Hex(v));
    }
  }

  @Nested
  @DisplayName("randomUrlSafe")
  class RandomUrlSafe {

    @Test
    @DisplayName("bytes=64 호출 시 Base64url 86자를 반환한다 (패딩 없음)")
    void length64BytesProduces86Chars() {
      // Base64url without padding: ceil(64 * 8 / 6) = 86
      String token = TokenCrypto.randomUrlSafe(64);
      assertThat(token).hasSize(86);
    }

    @Test
    @DisplayName("URL-safe Base64 문자만 포함한다 (패딩 없음)")
    void urlSafeCharsOnly() {
      String token = TokenCrypto.randomUrlSafe(64);
      assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("연속 두 호출은 서로 다른 값을 반환한다")
    void uniqueValues() {
      assertThat(TokenCrypto.randomUrlSafe(64)).isNotEqualTo(TokenCrypto.randomUrlSafe(64));
    }
  }
}
