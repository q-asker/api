package com.icc.qasker.global.query;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 요청 라이프사이클에 요청당 쿼리수를 묶는다. 시작 시 카운터를 0으로 리셋하고, 응답 커밋 전 X-Query-Count 헤더를 달고, 종료 시
 * Micrometer(app_db_queries{uri,method})에 기록 + reqId·uri와 함께 로그로 남긴다.
 * QueryInstrumentationConfig(@Profile loadtest)에서만 등록된다.
 */
public class QueryCountInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(QueryCountInterceptor.class);
  private final MeterRegistry registry;

  public QueryCountInterceptor(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    QueryCounter.reset();
    // MdcRequestFilter는 핸들러 매핑 전이라 raw URI만 안다. 여기(매핑 후·핸들러 전)서 uri를
    // 템플릿(BEST_MATCHING_PATTERN)으로 정제 → 이후 실행되는 쿼리 주석이 GET:/history/{id}로 묶인다.
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern != null) {
      MDC.put("uri", request.getMethod() + ":" + pattern);
    }
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable ModelAndView modelAndView) {
    response.addHeader("X-Query-Count", Integer.toString(QueryCounter.get()));
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable Exception ex) {
    int count = QueryCounter.get();
    String uri = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (uri == null) {
      uri = "UNKNOWN";
    }
    registry.summary("app.db.queries", "uri", uri, "method", request.getMethod()).record(count);
    log.info(
        "[query-count] reqId={} uri={} count={}",
        MDC.get("reqId"),
        MDC.get("uri"),
        QueryCounter.get());
    QueryCounter.clear();
  }
}
