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

@RestController
@Profile("loadtest")
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
