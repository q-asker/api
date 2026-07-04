package com.icc.qasker.auth.component;

import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.global.properties.JwtProperties;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** JWT 서명·검증의 단일 진입점. 알고리즘·시크릿 변경은 이 클래스만 수정하면 된다. */
@Component
@RequiredArgsConstructor
public class JwtProvider {

  private final JwtProperties jwtProperties;

  public String sign(User user) {
    return JWT.create()
        .withSubject(user.getUserId())
        .withClaim("userId", user.getUserId())
        .withClaim("nickname", user.getNickname())
        .withClaim("role", user.getRole())
        .withExpiresAt(
            new Date(System.currentTimeMillis() + jwtProperties.getAccessExpirationSecond() * 1000))
        .sign(Algorithm.HMAC512(jwtProperties.getSecret()));
  }

  /**
   * 토큰을 검증하고 userId claim을 반환한다. 만료·위조·손상 등 모든 검증 실패는 null로 흡수한다(익명 통과 정책).
   *
   * @return userId, 또는 검증 실패/claim 없음 시 null
   */
  public String verifyAndExtractUserId(String token) {
    try {
      var decoded = require(Algorithm.HMAC512(jwtProperties.getSecret())).build().verify(token);
      return decoded.getClaim("userId").asString();
    } catch (JWTVerificationException ex) {
      return null;
    }
  }
}
