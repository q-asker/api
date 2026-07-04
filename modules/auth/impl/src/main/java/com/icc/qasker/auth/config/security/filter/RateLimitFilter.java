package com.icc.qasker.auth.config.security.filter;

import com.icc.qasker.auth.component.ClientKeyResolver;
import com.icc.qasker.global.properties.RateLimitProperties;
import com.icc.qasker.global.ratelimit.RateLimitPlanResolver;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bucket4j Token Bucket 기반 Rate Limit 필터. JWT 인증 필터 이후에 실행되어 인증 정보를 활용한다. */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final ClientKeyResolver keyResolver;
  private final RateLimitPlanResolver planResolver;
  private final RateLimitProperties rateLimitProperties;
  private final RateLimitBucketRegistry bucketRegistry;
  private final RateLimitResponseWriter responseWriter;
  private final RateLimitMetrics metrics;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
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

    boolean isGlobal = planResolver.resolveGlobal(request);
    String clientKey = isGlobal ? "global" : keyResolver.resolve(request);
    String bucketKey = clientKey + ":" + tier.name();

    Bucket bucket = bucketRegistry.getOrCreate(bucketKey, tier, isGlobal);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    String scope = isGlobal ? "global" : "client";

    if (probe.isConsumed()) {
      metrics.incrementConsumed(tier.name(), scope);
      responseWriter.addRateLimitHeaders(response, tier, probe);
      chain.doFilter(request, response);
    } else {
      metrics.incrementRejected(tier.name(), scope);
      responseWriter.writeTooManyRequests(response, tier, probe);
    }
  }
}
