package com.icc.qasker.global.properties;

import com.icc.qasker.global.ratelimit.RateLimitTier;
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

  /** 등급별 제한 수치 오버라이드 */
  private final Map<RateLimitTier, TierConfig> tiers;

  /** 등급별 세부 설정 */
  @Getter
  @AllArgsConstructor
  public static class TierConfig {

    /** 버킷 최대 토큰 수 */
    private final long capacity;

    /** 분당 토큰 보충 수 */
    private final long refillPerMinute;
  }
}
