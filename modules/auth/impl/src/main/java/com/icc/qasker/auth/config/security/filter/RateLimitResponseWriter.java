package com.icc.qasker.auth.config.security.filter;

import com.icc.qasker.global.error.CustomErrorResponse;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Rate Limit 관련 응답 헤더 및 429 본문 직렬화를 담당한다. */
@Component
@RequiredArgsConstructor
public class RateLimitResponseWriter {

  private static final String HEADER_LIMIT = "X-RateLimit-Limit";
  private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
  private static final String HEADER_RESET = "X-RateLimit-Reset";
  private static final String HEADER_RETRY_AFTER = "Retry-After";

  private final ObjectMapper objectMapper;
  private final RateLimitBucketRegistry bucketRegistry;

  public void addRateLimitHeaders(
      HttpServletResponse response, RateLimitTier tier, ConsumptionProbe probe) {
    long resetEpochSeconds =
        Instant.now().plusNanos(probe.getNanosToWaitForRefill()).getEpochSecond();
    response.setHeader(HEADER_LIMIT, String.valueOf(bucketRegistry.getCapacity(tier)));
    response.setHeader(HEADER_REMAINING, String.valueOf(Math.max(0, probe.getRemainingTokens())));
    response.setHeader(HEADER_RESET, String.valueOf(resetEpochSeconds));
  }

  public void writeTooManyRequests(
      HttpServletResponse response, RateLimitTier tier, ConsumptionProbe probe) throws IOException {
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
