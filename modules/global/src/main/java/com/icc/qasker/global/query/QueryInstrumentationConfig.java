package com.icc.qasker.global.query;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 부하 테스트 전용 쿼리 계측 배선. Hibernate에 CountingInspector를 꽂고, 요청 라이프사이클에 QueryCountInterceptor를
 * 등록한다. @Profile("loadtest")이라 부하 테스트 프로파일에서만 로드되고, 일반 local·prod에는 영향이 없다.
 */
@Configuration
@Profile("loadtest")
public class QueryInstrumentationConfig implements WebMvcConfigurer {

  private final MeterRegistry meterRegistry;

  public QueryInstrumentationConfig(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /** Hibernate SessionFactory에 StatementInspector를 등록 — 모든 SQL이 inspect()를 거친다. */
  @Bean
  public HibernatePropertiesCustomizer statementInspectorCustomizer() {
    CountingInspector inspector = new CountingInspector();
    return (Map<String, Object> props) ->
        props.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
  }

  /** reqId·uri를 MDC에 싣는 필터. @Profile("loadtest")이라 부하 테스트 때만 체인에 오른다. */
  @Bean
  public MdcRequestFilter mdcRequestFilter() {
    return new MdcRequestFilter();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new QueryCountInterceptor(meterRegistry));
  }
}
