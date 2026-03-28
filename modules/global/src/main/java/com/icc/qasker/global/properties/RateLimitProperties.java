package com.icc.qasker.global.properties;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Rate Limit 설정 바인딩 클래스. application.yml의 q-asker.rate-limit 프리픽스와 매핑된다. */
@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "q-asker.rate-limit")
public class RateLimitProperties {

  /** Rate Limiter 활성화 여부 */
  private final boolean enabled;

  /** 비활성 버킷 만료 시간 (분) */
  private final long bucketExpireMinutes;

  /** 최대 버킷 수 (OOM 방지) */
  private final long maxBucketSize;

  /**
   * Tier별 capacity/refillPerMinute 설정. 키는 RateLimitTier enum 이름(예: CRITICAL, HEAVY). YAML에 없는 Tier를
   * 사용하면 애플리케이션 시작 시 예외가 발생한다.
   */
  private final Map<String, TierConfig> tiers;

  /** Tier별 버킷 설정. capacity: 버킷 최대 토큰 수, refillPerMinute: 분당 토큰 보충 수 */
  public record TierConfig(long capacity, long refillPerMinute) {}
}
