package com.icc.qasker.auth.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.icc.qasker.auth.component.PrincipalExtractor;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.principal.UserPrincipal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * UserIdArgumentResolver 회귀 테스트.
 *
 * <p>PrincipalExtractor 통합 리팩터링의 안전망. User/UserPrincipal/String/anonymous 각 케이스의 반환값이 리팩터링 전후로 동일함을
 * 보장한다.
 */
class UserIdArgumentResolverTest {

  private UserIdArgumentResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new UserIdArgumentResolver(new PrincipalExtractor());
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private Object resolve() throws Exception {
    return resolver.resolveArgument(
        mock(org.springframework.core.MethodParameter.class),
        null,
        mock(org.springframework.web.context.request.NativeWebRequest.class),
        null);
  }

  @Nested
  @DisplayName("User principal")
  class UserPrincipalCase {

    @Test
    @DisplayName("User 타입이면 userId를 반환한다")
    void returnsUserId() throws Exception {
      User user = User.builder().userId("user-123").role("ROLE_USER").build();
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  user, null, AuthorityUtils.createAuthorityList("ROLE_USER")));

      assertThat(resolve()).isEqualTo("user-123");
    }
  }

  @Nested
  @DisplayName("UserPrincipal (OAuth2)")
  class OAuth2PrincipalCase {

    @Test
    @DisplayName("UserPrincipal 타입이면 내부 User의 userId를 반환한다")
    void returnsUserId() throws Exception {
      User user = User.builder().userId("oauth-456").role("ROLE_USER").build();
      UserPrincipal principal = new UserPrincipal(user, Map.of());
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principal, null, AuthorityUtils.createAuthorityList("ROLE_USER")));

      assertThat(resolve()).isEqualTo("oauth-456");
    }
  }

  @Nested
  @DisplayName("String principal")
  class StringPrincipalCase {

    @Test
    @DisplayName("비어 있지 않은 String이면 그대로 반환한다")
    void returnsString() throws Exception {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "user-789", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

      assertThat(resolve()).isEqualTo("user-789");
    }

    @Test
    @DisplayName("'anonymousUser' 문자열이면 null을 반환한다")
    void anonymousUserStringReturnsNull() throws Exception {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

      assertThat(resolve()).isNull();
    }
  }

  @Nested
  @DisplayName("비인증 / anonymous")
  class Unauthenticated {

    @Test
    @DisplayName("SecurityContext가 비어 있으면 null을 반환한다")
    void nullAuthentication() throws Exception {
      assertThat(resolve()).isNull();
    }

    @Test
    @DisplayName("AnonymousAuthenticationToken이면 null을 반환한다")
    void anonymousTokenReturnsNull() throws Exception {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new AnonymousAuthenticationToken(
                  "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

      assertThat(resolve()).isNull();
    }
  }
}
