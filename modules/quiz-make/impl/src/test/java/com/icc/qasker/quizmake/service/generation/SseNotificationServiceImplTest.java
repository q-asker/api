package com.icc.qasker.quizmake.service.generation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.icc.qasker.quizmake.properties.QAskerSseProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseNotificationServiceImplTest {

  private SseNotificationServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SseNotificationServiceImpl(
            new QAskerSseProperties(),
            CircuitBreakerRegistry.ofDefaults(),
            new SimpleMeterRegistry());
  }

  @Test
  @DisplayName("전송 중 IOException이 발생하면 completeWithError를 호출하고 예외를 전파하지 않는다")
  void send_failure_completes_with_error_without_propagating() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    IOException failure = new IOException("broken pipe");
    doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
    putEmitter("session-1", emitter);

    assertThatCode(() -> service.sendComplete("session-1")).doesNotThrowAnyException();

    verify(emitter).completeWithError(failure);
  }

  @Test
  @DisplayName("등록된 emitter가 없으면 전송은 no-op이며 예외를 던지지 않는다")
  void send_is_noop_when_emitter_absent() {
    assertThatCode(() -> service.sendComplete("unknown-session")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("sendCreatedMessageWithId도 IOException 시 completeWithError로 마감한다")
  void send_created_message_completes_with_error_on_failure() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    IOException failure = new IOException("broken pipe");
    doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
    putEmitter("session-2", emitter);

    assertThatCode(() -> service.sendCreatedMessageWithId("session-2", "3", "data"))
        .doesNotThrowAnyException();

    verify(emitter).completeWithError(failure);
  }

  @Test
  @DisplayName("정상 전송 시 completeWithError를 호출하지 않는다")
  void successful_send_does_not_complete_with_error() {
    SseEmitter emitter = mock(SseEmitter.class);
    putEmitter("session-3", emitter);

    service.sendComplete("session-3");

    verify(emitter, never()).completeWithError(any());
  }

  @SuppressWarnings("unchecked")
  private void putEmitter(String sessionId, SseEmitter emitter) {
    try {
      Field field = SseNotificationServiceImpl.class.getDeclaredField("emitterMap");
      field.setAccessible(true);
      ((Map<String, SseEmitter>) field.get(service)).put(sessionId, emitter);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
