package com.icc.qasker.auth.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.auth.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ClientKeyResolver 회귀 테스트.
 *
 * <p>PrincipalExtractor 통합 리팩터링의 안전망. 인증/비인증 시 키 포맷이 리팩터링 전후로 동일함을 보장한다.
 */
class ClientKeyResolverTest {

  private ClientKeyResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new ClientKeyResolver(new PrincipalExtractor());
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("인증된 사용자")
  class Authenticated {

    @Test
    @DisplayName("User principal이면 'user:{userId}' 형식을 반환한다")
    void returnsUserKey() {
      User user = User.builder().userId("user-abc").role("ROLE_USER").build();
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  user, null, AuthorityUtils.createAuthorityList("ROLE_USER")));

      assertThat(resolver.resolve(new MockHttpServletRequest())).isEqualTo("user:user-abc");
    }
  }

  @Nested
  @DisplayName("비인증 사용자")
  class Unauthenticated {

    @Test
    @DisplayName("인증 없으면 'ip:{remoteAddr}' 형식을 반환한다")
    void returnsIpKey() {
      MockHttpServletRequest req = new MockHttpServletRequest();
      req.setRemoteAddr("192.168.1.1");

      assertThat(resolver.resolve(req)).isEqualTo("ip:192.168.1.1");
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더가 있으면 첫 번째 IP를 사용한다")
    void usesFirstForwardedIp() {
      MockHttpServletRequest req = new MockHttpServletRequest();
      req.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");

      assertThat(resolver.resolve(req)).isEqualTo("ip:10.0.0.1");
    }
  }
}
