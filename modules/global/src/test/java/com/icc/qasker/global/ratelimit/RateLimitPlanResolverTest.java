package com.icc.qasker.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@DisplayName("RateLimitPlanResolver 등급/글로벌 결정 검증")
class RateLimitPlanResolverTest {

  private RequestMappingHandlerMapping handlerMapping;
  private RateLimitPlanResolver resolver;
  private final HttpServletRequest request = mock(HttpServletRequest.class);

  @BeforeEach
  void setUp() {
    handlerMapping = mock(RequestMappingHandlerMapping.class);
    resolver = new RateLimitPlanResolver(handlerMapping);
  }

  private HandlerExecutionChain chainFor(String methodName) throws Exception {
    Method method = TestHandler.class.getMethod(methodName);
    return new HandlerExecutionChain(new HandlerMethod(new TestHandler(), method));
  }

  @Test
  @DisplayName("@RateLimit 있는 핸들러 → 지정 tier와 global 값 반환")
  void annotatedHandler() throws Exception {
    when(handlerMapping.getHandler(request)).thenReturn(chainFor("annotated"));

    assertThat(resolver.resolve(request)).isEqualTo(RateLimitTier.CRITICAL);
    assertThat(resolver.resolveGlobal(request)).isTrue();
  }

  @Test
  @DisplayName("@RateLimit 없는 핸들러 → 기본값 READ / false")
  void unannotatedHandler() throws Exception {
    when(handlerMapping.getHandler(request)).thenReturn(chainFor("plain"));

    assertThat(resolver.resolve(request)).isEqualTo(RateLimitTier.READ);
    assertThat(resolver.resolveGlobal(request)).isFalse();
  }

  @Test
  @DisplayName("핸들러 없음(null) → 기본값 READ / false")
  void noHandler() throws Exception {
    when(handlerMapping.getHandler(request)).thenReturn(null);

    assertThat(resolver.resolve(request)).isEqualTo(RateLimitTier.READ);
    assertThat(resolver.resolveGlobal(request)).isFalse();
  }

  @Test
  @DisplayName("핸들러 조회 실패(예외) → 기본값 READ / false")
  void lookupFails() throws Exception {
    when(handlerMapping.getHandler(request)).thenThrow(new RuntimeException("boom"));

    assertThat(resolver.resolve(request)).isEqualTo(RateLimitTier.READ);
    assertThat(resolver.resolveGlobal(request)).isFalse();
  }

  static class TestHandler {

    @RateLimit(value = RateLimitTier.CRITICAL, global = true)
    public void annotated() {}

    public void plain() {}
  }
}
