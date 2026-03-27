package com.icc.qasker.auth.component;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 요청에서 Rate Limit 버킷 키를 추출한다. 인증 사용자: "user:{userId}", 비인증: "ip:{ip}" */
@Component
public class ClientKeyResolver {

  public String resolve(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // anonymousUser는 String 타입 principal을 가짐 → 미인증으로 처리
    if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
      return "user:" + auth.getName();
    }
    return "ip:" + extractIp(request);
  }

  private String extractIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return request.getRemoteAddr();
    }
    // 여러 프록시를 거친 경우 첫 번째 IP가 실제 클라이언트 IP
    return forwarded.split(",")[0].trim();
  }
}
