package com.icc.qasker.auth.config.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.icc.qasker.auth.component.ClientKeyResolver;
import com.icc.qasker.global.error.CustomErrorResponse;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.RateLimitProperties;
import com.icc.qasker.global.ratelimit.RateLimitPlanResolver;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bucket4j Token Bucket 기반 Rate Limit 필터. JWT 인증 필터 이후에 실행되어 인증 정보를 활용한다. */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String HEADER_LIMIT = "X-RateLimit-Limit";
  private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
  private static final String HEADER_RESET = "X-RateLimit-Reset";
  private static final String HEADER_RETRY_AFTER = "Retry-After";

  private final ClientKeyResolver keyResolver;
  private final RateLimitPlanResolver planResolver;
  private final RateLimitProperties rateLimitProperties;
  private final ObjectMapper objectMapper;

  private Cache<String, Bucket> bucketCache;

  @PostConstruct
  public void initCache() {
    bucketCache =
        Caffeine.newBuilder()
            .expireAfterAccess(rateLimitProperties.getBucketExpireMinutes(), TimeUnit.MINUTES)
            .maximumSize(rateLimitProperties.getMaxBucketSize())
            .build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // actuator 엔드포인트는 Rate Limit 제외
    return request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!rateLimitProperties.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    RateLimitTier tier = planResolver.resolve(request);
    if (tier == RateLimitTier.NONE) {
      chain.doFilter(request, response);
      return;
    }

    String clientKey = keyResolver.resolve(request);
    String bucketKey = clientKey + ":" + tier.name();
    Bucket bucket = bucketCache.get(bucketKey, k -> createBucket(tier));

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      addRateLimitHeaders(response, tier, probe);
      chain.doFilter(request, response);
    } else {
      long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
      response.setStatus(429);
      response.setContentType("application/json;charset=UTF-8");
      response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));
      addRateLimitHeaders(response, tier, probe);
      objectMapper.writeValue(
          response.getWriter(),
          new CustomErrorResponse(ExceptionMessage.RATE_LIMIT_EXCEEDED.getMessage()));
    }
  }

  private void addRateLimitHeaders(
      HttpServletResponse response, RateLimitTier tier, ConsumptionProbe probe) {
    long resetEpochSeconds =
        Instant.now().plusNanos(probe.getNanosToWaitForRefill()).getEpochSecond();
    response.setHeader(HEADER_LIMIT, String.valueOf(getCapacity(tier)));
    response.setHeader(HEADER_REMAINING, String.valueOf(Math.max(0, probe.getRemainingTokens())));
    response.setHeader(HEADER_RESET, String.valueOf(resetEpochSeconds));
  }

  private Bucket createBucket(RateLimitTier tier) {
    Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(getCapacity(tier))
            .refillGreedy(getRefillPerMinute(tier), Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(bandwidth).build();
  }

  private long getCapacity(RateLimitTier tier) {
    return tier.getDefaultCapacity();
  }

  private long getRefillPerMinute(RateLimitTier tier) {
    return tier.getDefaultRefillPerMinute();
  }
}
