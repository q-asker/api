package com.icc.qasker.global.ratelimit;

import com.icc.qasker.global.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 요청에 적용할 {@link RateLimitTier}를 결정한다.
 *
 * <p>핸들러 메서드에 선언된 {@link RateLimit} 어노테이션 값을 반환하며, 어노테이션이 없으면 기본값 {@link RateLimitTier#READ}를 반환한다.
 */
@Component
public class RateLimitPlanResolver {

  private final RequestMappingHandlerMapping handlerMapping;

  public RateLimitPlanResolver(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  public RateLimitTier resolve(HttpServletRequest request) {
    try {
      HandlerExecutionChain chain = handlerMapping.getHandler(request);
      if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit != null) {
          return rateLimit.value();
        }
      }
    } catch (Exception ignored) {
      // 핸들러 조회 실패 시 기본값 사용
    }
    return RateLimitTier.READ;
  }

  /** 요청 핸들러에 global=true 가 선언되어 있으면 true를 반환한다. */
  public boolean resolveGlobal(HttpServletRequest request) {
    try {
      HandlerExecutionChain chain = handlerMapping.getHandler(request);
      if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit != null) {
          return rateLimit.global();
        }
      }
    } catch (Exception ignored) {
      // 핸들러 조회 실패 시 기본값 사용
    }
    return false;
  }
}
