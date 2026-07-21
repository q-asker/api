package com.icc.qasker.loadtest;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/**
 * {@link RequestResourceMetricsFilter} 명시 등록. Boot 4.x에서 Filter 빈 자동 등록이 동작하지 않아
 * (Boot4CompatConfig와 같은 부류의 보완) FilterRegistrationBean으로 직접 서블릿 체인에 건다.
 */
@Configuration
@Profile("loadtest")
public class RequestResourceMetricsConfig {

  @Bean
  public FilterRegistrationBean<RequestResourceMetricsFilter> requestResourceMetricsRegistration(
      RequestResourceMetricsFilter filter) {
    FilterRegistrationBean<RequestResourceMetricsFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns("/*");
    // 보안 체인·다른 필터보다 앞 — 요청 처리 전체 구간을 계측 범위에 포함
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
