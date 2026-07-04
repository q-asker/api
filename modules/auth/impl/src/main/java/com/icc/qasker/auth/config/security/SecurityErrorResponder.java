package com.icc.qasker.auth.config.security;

import com.icc.qasker.global.error.CustomErrorResponse;
import com.icc.qasker.global.error.ExceptionMessage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 401/403 에러 응답을 CustomErrorResponse 포맷으로 통일하여 RateLimitFilter(429)와 스키마를 맞춘다. */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

  private final ObjectMapper objectMapper;

  public void writeUnauthorized(HttpServletResponse response) throws IOException {
    write(
        response, HttpServletResponse.SC_UNAUTHORIZED, ExceptionMessage.UNAUTHORIZED.getMessage());
  }

  public void writeForbidden(HttpServletResponse response) throws IOException {
    write(
        response,
        HttpServletResponse.SC_FORBIDDEN,
        ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
  }

  private void write(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    objectMapper.writeValue(response.getWriter(), new CustomErrorResponse(message));
  }
}
