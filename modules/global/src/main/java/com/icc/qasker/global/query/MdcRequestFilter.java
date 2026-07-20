package com.icc.qasker.global.query;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 시작 시 reqId(상관관계 ID)와 uri를 MDC에 넣어, 같은 스레드에서 실행되는 CountingInspector가 SQL 주석에 박고
 * QueryCountInterceptor가 로그에 남길 수 있게 한다. 종료 시 MDC를 비워 스레드 재사용 오염을 막는다.
 * QueryInstrumentationConfig(@Profile local)에서만 등록된다.
 */
public class MdcRequestFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      MDC.put("reqId", UUID.randomUUID().toString().substring(0, 8));
      MDC.put("uri", request.getMethod() + ":" + request.getRequestURI());
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
