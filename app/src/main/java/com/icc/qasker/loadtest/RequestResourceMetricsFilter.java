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
@Profile("local")
public class RequestResourceMetricsFilter extends OncePerRequestFilter {

  private static final com.sun.management.ThreadMXBean THREADS =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

  /** 측정 엔드포인트 — 아래 선등록의 대상. */
  private static final String MEASURED_URI = "/admin/problem-sets/explanation-review";

  private final MeterRegistry registry;

  public RequestResourceMetricsFilter(MeterRegistry registry) {
    this.registry = registry;
    // 측정 uri의 카운터 3종을 앱 시작 시 0으로 선등록 — 시리즈가 부하 시작 전부터 존재해야
    // 대시보드의 increase() 계산이 "첫 스크레이프 이전 증가분(머리)"을 잃지 않는다.
    // (JfrMethodTimingExporter의 게이지 선등록과 같은 이유 — 분자·분모의 출생 시점을 맞춘다.)
    registry.counter("request.count", "uri", MEASURED_URI);
    registry.counter("request.thread.cpu.seconds", "uri", MEASURED_URI);
    registry.counter("request.thread.allocated.bytes", "uri", MEASURED_URI);
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
      registry.counter("request.count", "uri", uri).increment();
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
