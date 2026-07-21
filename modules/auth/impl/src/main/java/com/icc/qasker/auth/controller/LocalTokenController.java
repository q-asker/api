package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.component.JwtProvider;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬/부하 전용 JWT 발급 헬퍼. {@code GET /local/token?userId=} 로 기존 User의 액세스 토큰을 즉시 발급해 OAuth 로그인 없이 인증이
 * 필요한 API(E2E·수동 테스트·부하)를 태울 수 있게 한다.
 *
 * <p>{@code local} 프로파일에서만 빈으로 등록된다. {@code prod} 에는 절대 노출되지 않는다(프로파일 게이트).
 */
@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping("/local")
public class LocalTokenController {

  private final UserRepository userRepository;
  private final JwtProvider jwtProvider;

  @RateLimit(RateLimitTier.NONE)
  @GetMapping("/token")
  public ResponseEntity<String> provideToken(@RequestParam String userId) {
    return userRepository
        .findById(userId)
        .map(user -> ResponseEntity.ok(jwtProvider.sign(user)))
        .orElseGet(() -> ResponseEntity.status(404).body("unknown userId: " + userId));
  }
}
