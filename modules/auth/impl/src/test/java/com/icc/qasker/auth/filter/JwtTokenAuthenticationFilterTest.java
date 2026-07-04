package com.icc.qasker.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.auth.component.JwtProvider;
import com.icc.qasker.auth.config.security.filter.JwtTokenAuthenticationFilter;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.properties.JwtProperties;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JwtTokenAuthenticationFilter 회귀 테스트.
 *
 * <p>JwtProvider 추출 리팩터링의 안전망. 유효/만료/위조/손상 토큰 각각의 동작이 리팩터링 전후로 동일함을 보장한다.
 */
class JwtTokenAuthenticationFilterTest {

  private static final String SECRET =
      "test-secret-key-that-is-long-enough-for-hmac512-algorithm-32bytes-minimum";
  private static final String USER_ID = "user-abc";

  private JwtProvider jwtProvider;
  private UserRepository userRepository;
  private JwtTokenAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties(SECRET, 3600L, 86400L);
    jwtProvider = new JwtProvider(jwtProperties);
    userRepository = mock(UserRepository.class);
    filter =
        new JwtTokenAuthenticationFilter(
            mock(AuthenticationManager.class), userRepository, jwtProvider);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private String validToken() {
    return JWT.create()
        .withClaim("userId", USER_ID)
        .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC512(SECRET));
  }

  private String expiredToken() {
    return JWT.create()
        .withClaim("userId", USER_ID)
        .withExpiresAt(new Date(System.currentTimeMillis() - 1_000))
        .sign(Algorithm.HMAC512(SECRET));
  }

  private String forgedToken() {
    return JWT.create()
        .withClaim("userId", USER_ID)
        .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC512("different-secret-that-is-long-enough-for-hmac512-algorithm-here"));
  }

  private MockHttpServletRequest requestWithBearer(String token) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    return req;
  }

  @Nested
  @DisplayName("유효 토큰")
  class ValidToken {

    @Test
    @DisplayName("SecurityContext에 User principal과 role이 세팅된다")
    void setsAuthentication() throws Exception {
      User user = User.builder().userId(USER_ID).role("ROLE_USER").build();
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

      filter.doFilter(
          requestWithBearer(validToken()), new MockHttpServletResponse(), new MockFilterChain());

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertThat(auth).isNotNull();
      assertThat(auth.isAuthenticated()).isTrue();
      assertThat(((User) auth.getPrincipal()).getUserId()).isEqualTo(USER_ID);
      assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("DB에 존재하지 않는 userId면 익명으로 통과한다")
    void userNotFoundPassesAnonymously() throws Exception {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      filter.doFilter(
          requestWithBearer(validToken()), new MockHttpServletResponse(), new MockFilterChain());

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Nested
  @DisplayName("만료 토큰")
  class ExpiredToken {

    @Test
    @DisplayName("익명으로 통과한다 (SecurityContext 비어 있음)")
    void passesAnonymously() throws Exception {
      filter.doFilter(
          requestWithBearer(expiredToken()), new MockHttpServletResponse(), new MockFilterChain());

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Nested
  @DisplayName("위조/손상 토큰")
  class ForgedToken {

    @Test
    @DisplayName("위조 서명 토큰은 500이 아니라 익명으로 통과한다 (보안 회귀 고정)")
    void forgedSignaturePassesAnonymously() throws Exception {
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(requestWithBearer(forgedToken()), response, new MockFilterChain());

      assertThat(response.getStatus()).isNotEqualTo(500);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("손상된 토큰(형식 오류)은 익명으로 통과한다")
    void malformedTokenPassesAnonymously() throws Exception {
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(requestWithBearer("not.a.jwt"), response, new MockFilterChain());

      assertThat(response.getStatus()).isNotEqualTo(500);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Nested
  @DisplayName("Authorization 헤더 없음 / 비정상")
  class NoAuthHeader {

    @Test
    @DisplayName("헤더가 없으면 익명으로 통과한다")
    void noHeader() throws Exception {
      filter.doFilter(
          new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 익명으로 통과한다")
    void noBearerPrefix() throws Exception {
      MockHttpServletRequest req = new MockHttpServletRequest();
      req.addHeader("Authorization", validToken());

      filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("userId claim이 비어 있으면 익명으로 통과한다")
    void emptyUserIdClaim() throws Exception {
      String token =
          JWT.create()
              .withClaim("userId", "")
              .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
              .sign(Algorithm.HMAC512(SECRET));

      filter.doFilter(
          requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }
}
