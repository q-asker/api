package com.icc.qasker.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@DisplayName("GlobalExceptionHandler 예외→HTTP 응답/로깅 변환 검증")
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger handlerLogger;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    handlerLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("CustomException(4xx) → 해당 status + body message + WARN 로그")
  void customException4xx() throws Exception {
    mockMvc
        .perform(get("/custom-4xx"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));

    assertThat(logAppender.list).anyMatch(e -> e.getLevel() == Level.WARN);
  }

  @Test
  @DisplayName("CustomException(5xx, context 포함) → status + [context] ERROR 로그")
  void customException5xxWithContext() throws Exception {
    mockMvc
        .perform(get("/custom-5xx"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));

    assertThat(logAppender.list)
        .anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("userId=5"));
  }

  @Test
  @DisplayName("CallNotPermittedException → 500 + AI 통신 오류 메시지")
  void circuitBreakerOpen() throws Exception {
    mockMvc
        .perform(get("/circuit"))
        .andExpect(status().isInternalServerError())
        .andExpect(
            jsonPath("$.message")
                .value(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
  }

  @Test
  @DisplayName("MaxUploadSizeExceededException → FILE_SIZE_EXCEEDED status/message")
  void maxUploadSize() throws Exception {
    mockMvc
        .perform(get("/upload-too-large"))
        .andExpect(status().is(ExceptionMessage.FILE_SIZE_EXCEEDED.getHttpStatus().value()))
        .andExpect(jsonPath("$.message").value(ExceptionMessage.FILE_SIZE_EXCEEDED.getMessage()));
  }

  @Test
  @DisplayName("ClientAbortException(SSE) → 무응답(200, 빈 본문) 정상 종료")
  void sseClientAbort() throws Exception {
    mockMvc.perform(get("/sse-abort")).andExpect(status().isOk()).andExpect(content().string(""));
  }

  @Test
  @DisplayName("AsyncRequestNotUsableException(SSE) → 무응답(200, 빈 본문) 정상 종료")
  void sseAsyncNotUsable() throws Exception {
    mockMvc.perform(get("/sse-async")).andExpect(status().isOk()).andExpect(content().string(""));
  }

  @Test
  @DisplayName("AsyncRequestTimeoutException → 503 + AI 통신 오류 메시지")
  void asyncTimeout() throws Exception {
    mockMvc
        .perform(get("/async-timeout"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.message")
                .value(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
  }

  @Test
  @DisplayName("미처리 Exception → 500 + DEFAULT_ERROR 메시지")
  void unhandledException() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value(ExceptionMessage.DEFAULT_ERROR.getMessage()));
  }

  @RestController
  static class TestController {

    @GetMapping("/custom-4xx")
    public void custom4xx() {
      throw new CustomException(ExceptionMessage.USER_NOT_FOUND);
    }

    @GetMapping("/custom-5xx")
    public void custom5xx() {
      throw new CustomException(ExceptionMessage.DEFAULT_ERROR, "userId=5");
    }

    @GetMapping("/circuit")
    public void circuit() {
      throw CallNotPermittedException.createCallNotPermittedException(
          CircuitBreaker.ofDefaults("test"));
    }

    @GetMapping("/upload-too-large")
    public void uploadTooLarge() {
      throw new MaxUploadSizeExceededException(10L);
    }

    @GetMapping("/sse-abort")
    public void sseAbort() throws ClientAbortException {
      throw new ClientAbortException("client gone");
    }

    @GetMapping("/sse-async")
    public void sseAsync() throws AsyncRequestNotUsableException {
      throw new AsyncRequestNotUsableException("async not usable");
    }

    @GetMapping("/async-timeout")
    public void asyncTimeout() {
      throw new AsyncRequestTimeoutException();
    }

    @GetMapping("/boom")
    public void boom() {
      throw new RuntimeException("unexpected");
    }
  }
}
