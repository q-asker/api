package com.icc.qasker.auth.component;

import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.principal.UserPrincipal;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** SecurityContext에서 userId를 추출하는 단일 진입점. ClientKeyResolver와 UserIdArgumentResolver가 공유한다. */
@Component
public class PrincipalExtractor {

  public Optional<String> extractUserId(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof User user) {
      return Optional.of(user.getUserId());
    }
    if (principal instanceof UserPrincipal userPrincipal) {
      return Optional.of(userPrincipal.getUser().getUserId());
    }
    if (principal instanceof String s && !s.isBlank() && !"anonymousUser".equalsIgnoreCase(s)) {
      return Optional.of(s);
    }
    return Optional.empty();
  }
}
