package com.icc.qasker.auth.config.security.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.icc.qasker.global.properties.RateLimitProperties;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 버킷 생성·캐싱·Gauge 등록을 담당한다. Gauge WeakReference 문제를 피하기 위해 글로벌 버킷은 강참조 Map으로 따로 보관한다. */
@Component
@RequiredArgsConstructor
public class RateLimitBucketRegistry {

  private final RateLimitProperties rateLimitProperties;
  private final MeterRegistry meterRegistry;

  private Cache<String, Bucket> bucketCache;

  // 글로벌 버킷은 만료 없이 영구 보관 — Micrometer Gauge의 WeakReference가 끊기지 않도록 강참조 유지
  private final Map<String, Bucket> globalBucketStore = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    bucketCache =
        Caffeine.newBuilder()
            .expireAfterAccess(rateLimitProperties.getBucketExpireMinutes(), TimeUnit.MINUTES)
            .maximumSize(rateLimitProperties.getMaxBucketSize())
            .build();
  }

  public Bucket getOrCreate(String bucketKey, RateLimitTier tier, boolean isGlobal) {
    if (isGlobal) {
      // 글로벌 버킷은 Caffeine 캐시 만료 대상에서 제외 — Gauge WeakReference가 끊기지 않도록 강참조 유지
      return globalBucketStore.computeIfAbsent(
          bucketKey,
          k -> {
            Bucket b = createBucket(tier);
            meterRegistry.gauge(
                "rate_limit_global_remaining_tokens",
                Tags.of("tier", tier.name()),
                b,
                bkt -> (double) bkt.getAvailableTokens());
            return b;
          });
    }
    return bucketCache.get(bucketKey, k -> createBucket(tier));
  }

  public long getCapacity(RateLimitTier tier) {
    return getTierConfig(tier).capacity();
  }

  private Bucket createBucket(RateLimitTier tier) {
    Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(getCapacity(tier))
            .refillGreedy(getTierConfig(tier).refillPerMinute(), Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(bandwidth).build();
  }

  private RateLimitProperties.TierConfig getTierConfig(RateLimitTier tier) {
    RateLimitProperties.TierConfig config = rateLimitProperties.getTiers().get(tier.name());
    if (config == null) {
      throw new IllegalStateException(
          "YAML에 Rate Limit Tier 설정이 없습니다: q-asker.rate-limit.tiers." + tier.name());
    }
    return config;
  }
}
