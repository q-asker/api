package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.LogoutService;
import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.dto.response.RotateTokenResponse;
import com.icc.qasker.auth.util.CookieUtil;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

  private final TokenRotationService tokenRotationService;
  private final LogoutService logoutService;

  @Operation(summary = "인증 테스트 엔드포인트")
  @RateLimit(RateLimitTier.NONE)
  @GetMapping("/test")
  public ResponseEntity<?> test() {
    System.out.println("test 성공");
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "리프레시 토큰으로 액세스 토큰을 재발급한다")
  @RateLimit(RateLimitTier.STANDARD)
  @PostMapping("/refresh")
  public ResponseEntity<RotateTokenResponse> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    var rtCookie =
        CookieUtil.getCookie(request, "refresh_token")
            .orElseThrow(() -> new CustomException(ExceptionMessage.UNAUTHORIZED));
    return ResponseEntity.ok(tokenRotationService.rotateTokens(rtCookie.getValue(), response));
  }

  @Operation(summary = "로그아웃 처리 (리프레시 토큰 폐기)")
  @RateLimit(RateLimitTier.STANDARD)
  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    logoutService.logout(request, response);
    return ResponseEntity.ok().build();
  }
}
