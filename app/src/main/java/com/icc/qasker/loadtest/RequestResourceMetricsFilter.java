package com.icc.qasker.loadtest;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청 단위 자원 격리 계측. 요청을 처리한 스레드의 CPU 시간·힙 할당 바이트 델타를 uri별 카운터로 누적한다 — process_cpu_time(부팅·GC·유휴 포함)이나
 * 힙 게이지(GC 타이밍 의존)와 달리 "이 요청이 소비한 자원"만 잡힌다.
 *
 * <ul>
 *   <li>{@code request_thread_cpu_seconds_total} — 요청 스레드가 소비한 CPU 시간 합
 *   <li>{@code request_thread_allocated_bytes_total} — 요청 스레드가 할당한 힙 바이트 합
 * </ul>
 *
 * <p>대시보드(qasker-enh-rw "최신 시도" 행)에서 {@code sum(last_over_time(...[$__range])) / 요청수}로 요청당 값을 계산한다.
 * 전제: 플랫폼 스레드 + 동기 MVC — ThreadMXBean CPU/할당 측정은 가상 스레드에서 -1을 반환하므로 (JDK-8303251) 음수 가드로 조용히 무시된다.
 * 가상 스레드 전환 시 이 지표는 무효가 된다.
 */
@Component
@Profile("loadtest")
public class RequestResourceMetricsFilter extends OncePerRequestFilter {

  private static final com.sun.management.ThreadMXBean THREADS =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

  private final MeterRegistry registry;

  public RequestResourceMetricsFilter(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long cpu0 = THREADS.getCurrentThreadCpuTime(); // 가상 스레드/미지원이면 -1
    long alloc0 = THREADS.getCurrentThreadAllocatedBytes();
    try {
      filterChain.doFilter(request, response);
    } finally {
      String uri = templatedUri(request);
      if (cpu0 >= 0) {
        long cpuDelta = THREADS.getCurrentThreadCpuTime() - cpu0;
        if (cpuDelta > 0) {
          registry
              .counter("request.thread.cpu.seconds", "uri", uri)
              .increment(cpuDelta / 1_000_000_000.0);
        }
      }
      if (alloc0 >= 0) {
        long allocDelta = THREADS.getCurrentThreadAllocatedBytes() - alloc0;
        if (allocDelta > 0) {
          registry.counter("request.thread.allocated.bytes", "uri", uri).increment(allocDelta);
        }
      }
    }
  }

  /** http_server_requests와 같은 템플릿 uri(예: /problem-set/{id})로 태깅해 카디널리티를 고정한다. */
  private static String templatedUri(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern instanceof String s ? s : request.getRequestURI();
  }
}
