package com.icc.qasker.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 4.x에서 auto-config 조건이 재편되어 우리가 필요로 하는 Bean들이 자동 등록되지 않는 것을 보완한다. Jackson
 * 3(tools.jackson) ObjectMapper는 Boot 4 auto-config가 담당하므로 여기서는 관여하지 않는다.
 */
@Configuration
public class Boot4CompatConfig {

  @Bean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager();
  }
}
