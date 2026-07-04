package com.icc.qasker.auth.config.security.filter;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Rate Limit consumed/rejected 카운터를 담당한다. */
@Component
@RequiredArgsConstructor
public class RateLimitMetrics {

  private final MeterRegistry meterRegistry;

  public void incrementConsumed(String tier, String scope) {
    meterRegistry.counter("rate_limit_consumed_total", "tier", tier, "scope", scope).increment();
  }

  public void incrementRejected(String tier, String scope) {
    meterRegistry.counter("rate_limit_rejected_total", "tier", tier, "scope", scope).increment();
  }
}
