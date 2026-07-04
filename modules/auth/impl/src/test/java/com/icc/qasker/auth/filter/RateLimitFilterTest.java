package com.icc.qasker.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.auth.component.ClientKeyResolver;
import com.icc.qasker.auth.config.security.filter.RateLimitBucketRegistry;
import com.icc.qasker.auth.config.security.filter.RateLimitFilter;
import com.icc.qasker.auth.config.security.filter.RateLimitMetrics;
import com.icc.qasker.auth.config.security.filter.RateLimitResponseWriter;
import com.icc.qasker.global.properties.RateLimitProperties;
import com.icc.qasker.global.ratelimit.RateLimitPlanResolver;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * RateLimitFilter 회귀 테스트.
 *
 * <p>책임 분리 리팩터링의 안전망. 통과/차단 조건, 응답 스키마, 메트릭 카운터가 리팩터링 전후로 동일함을 보장한다.
 */
class RateLimitFilterTest {

  private RateLimitPlanResolver planResolver;
  private ClientKeyResolver keyResolver;
  private SimpleMeterRegistry meterRegistry;
  private RateLimitFilter filter;
  private RateLimitBucketRegistry bucketRegistry;
  private RateLimitMetrics metrics;

  private RateLimitProperties props(boolean enabled) {
    RateLimitProperties.TierConfig cfg = new RateLimitProperties.TierConfig(5, 5);
    return new RateLimitProperties(enabled, 60, 10_000, Map.of("STANDARD", cfg, "READ", cfg));
  }

  @BeforeEach
  void setUp() {
    planResolver = mock(RateLimitPlanResolver.class);
    keyResolver = mock(ClientKeyResolver.class);
    meterRegistry = new SimpleMeterRegistry();

    RateLimitProperties properties = props(true);
    bucketRegistry = new RateLimitBucketRegistry(properties, meterRegistry);
    bucketRegistry.init();

    ObjectMapper objectMapper = new ObjectMapper();
    RateLimitResponseWriter responseWriter =
        new RateLimitResponseWriter(objectMapper, bucketRegistry);
    metrics = new RateLimitMetrics(meterRegistry);

    filter =
        new RateLimitFilter(
            keyResolver, planResolver, properties, bucketRegistry, responseWriter, metrics);
  }

  @Nested
  @DisplayName("필터 비활성화 / NONE tier")
  class Bypass {

    @Test
    @DisplayName("enabled=false 이면 무조건 통과한다")
    void disabledPassesThrough() throws Exception {
      RateLimitProperties disabledProps = props(false);
      RateLimitBucketRegistry reg = new RateLimitBucketRegistry(disabledProps, meterRegistry);
      reg.init();
      ObjectMapper om = new ObjectMapper();
      RateLimitFilter disabled =
          new RateLimitFilter(
              keyResolver,
              planResolver,
              disabledProps,
              reg,
              new RateLimitResponseWriter(om, reg),
              new RateLimitMetrics(meterRegistry));

      when(planResolver.resolve(any())).thenReturn(RateLimitTier.READ);

      MockHttpServletResponse response = new MockHttpServletResponse();
      disabled.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

      assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("tier=NONE 이면 버킷 소비 없이 통과한다")
    void noneTierPassesThrough() throws Exception {
      when(planResolver.resolve(any())).thenReturn(RateLimitTier.NONE);

      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

      assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("/actuator/** 는 rate limit이 적용되지 않는다 (capacity 초과해도 통과)")
    void actuatorNotRateLimited() throws Exception {
      when(planResolver.resolve(any())).thenReturn(RateLimitTier.STANDARD);
      when(planResolver.resolveGlobal(any())).thenReturn(false);
      when(keyResolver.resolve(any())).thenReturn("ip:127.0.0.1");

      MockHttpServletRequest req = new MockHttpServletRequest();
      req.setRequestURI("/actuator/health");

      // capacity(5) 초과해도 actuator 경로는 항상 통과해야 한다
      for (int i = 0; i < 10; i++) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
      }
    }
  }

  @Nested
  @DisplayName("버킷 소진 → 429")
  class BucketExhausted {

    @BeforeEach
    void stubTierAndKey() {
      when(planResolver.resolve(any())).thenReturn(RateLimitTier.STANDARD);
      when(planResolver.resolveGlobal(any())).thenReturn(false);
      when(keyResolver.resolve(any())).thenReturn("ip:127.0.0.1");
    }

    @Test
    @DisplayName("capacity(5) 소진 후 429 + Retry-After + X-RateLimit-* 헤더가 반환된다")
    void returns429WithHeaders() throws Exception {
      MockHttpServletRequest req = new MockHttpServletRequest();
      MockHttpServletResponse response = null;

      for (int i = 0; i < 5; i++) {
        response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
      }

      response = new MockHttpServletResponse();
      filter.doFilter(req, response, new MockFilterChain());

      assertThat(response.getStatus()).isEqualTo(429);
      assertThat(response.getHeader("Retry-After")).isNotNull();
      assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
      assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
      assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
    }

    @Test
    @DisplayName("429 응답 본문에 message 키가 포함된다")
    void responseBodyHasMessageKey() throws Exception {
      MockHttpServletRequest req = new MockHttpServletRequest();
      for (int i = 0; i < 5; i++) {
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
      }
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(req, response, new MockFilterChain());

      String body = response.getContentAsString();
      assertThat(body).contains("\"message\"");
    }
  }

  @Nested
  @DisplayName("메트릭 카운터")
  class Metrics {

    @BeforeEach
    void stubTierAndKey() {
      when(planResolver.resolve(any())).thenReturn(RateLimitTier.STANDARD);
      when(planResolver.resolveGlobal(any())).thenReturn(false);
      when(keyResolver.resolve(any())).thenReturn("ip:10.0.0.1");
    }

    @Test
    @DisplayName("요청 통과 시 consumed 카운터가 증가한다")
    void consumedCounterIncrement() throws Exception {
      filter.doFilter(
          new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

      double count =
          meterRegistry
              .counter("rate_limit_consumed_total", "tier", "STANDARD", "scope", "client")
              .count();
      assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("요청 차단 시 rejected 카운터가 증가한다")
    void rejectedCounterIncrement() throws Exception {
      MockHttpServletRequest req = new MockHttpServletRequest();
      for (int i = 0; i < 5; i++) {
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
      }
      filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

      double count =
          meterRegistry
              .counter("rate_limit_rejected_total", "tier", "STANDARD", "scope", "client")
              .count();
      assertThat(count).isEqualTo(1.0);
    }
  }
}
