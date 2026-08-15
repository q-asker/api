package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.global.error.ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR;

import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.infra.SseEmitterFactory;
import com.icc.qasker.quizmake.properties.QAskerSseProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class SseNotificationServiceImpl implements SseNotificationService {

  private final long sseTimeoutMs;
  private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final Counter sseTimeoutCounter;
  private final ScheduledExecutorService heartbeatScheduler;

  public SseNotificationServiceImpl(
      QAskerSseProperties sseProperties,
      CircuitBreakerRegistry circuitBreakerRegistry,
      MeterRegistry registry) {
    this.sseTimeoutMs = sseProperties.getTimeoutMs();
    this.circuitBreakerRegistry = circuitBreakerRegistry;

    Gauge.builder("sse.connections.active", emitterMap, Map::size)
        .description("활성 SSE 연결 수")
        .register(registry);
    this.sseTimeoutCounter =
        Counter.builder("sse.connections.timeout")
            .description("SSE 연결 타임아웃 발생 횟수")
            .register(registry);

    // 주기적으로 하트비트를 보내서 중간 프록시(클라우드플레어, Nginx)에서 세션을 끊는 것을 막는다
    this.heartbeatScheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "sse-heartbeat");
              t.setDaemon(true);
              return t;
            });
    long intervalMs = sseProperties.getHeartbeatIntervalMs();
    heartbeatScheduler.scheduleAtFixedRate(
        this::sendHeartbeats, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
  }

  /** 활성 emitter에 keep-alive comment를 보낸다. 전송 실패(끊긴 연결)면 맵에서 정리한다 — 다음 재연결 시 새 emitter가 등록된다. */
  private void sendHeartbeats() {
    emitterMap.forEach(
        (sessionId, emitter) -> {
          try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
          } catch (Exception e) {
            emitterMap.remove(sessionId, emitter);
          }
        });
  }

  @PreDestroy
  void shutdownHeartbeat() {
    heartbeatScheduler.shutdownNow();
  }

  @Override
  public SseEmitter createSseEmitter(String sessionId) {
    // [에러 | 새로고침 | 신규] 상황 구분할 수 없으므로 무조건 새로 만듦
    SseEmitter emitter = initSseEmitter(sessionId);

    // 새 emitter로 덮어 씌움
    SseEmitter old = emitterMap.put(sessionId, emitter);
    if (old != null) {
      old.complete();
    }

    // 서킷 브레이커 OPEN 상태이면 즉시 종료
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
    if (circuitBreaker.getState() == State.OPEN) {
      sendFinishWithError(sessionId, AI_SERVER_COMMUNICATION_ERROR.getMessage());
      return emitter;
    }

    // 연결 시작
    sendConnected(sessionId);
    return emitter;
  }

  @Override
  public void sendConnected(String sessionId) {
    safeSend(
        sessionId,
        SseEmitter.event().name("connected").data("hello"),
        "[SSE 연결 실패] 클라이언트 연결 중 에러 발생");
  }

  private @NonNull SseEmitter initSseEmitter(String sessionId) {
    SseEmitter emitter = SseEmitterFactory.createThreadSafeEmitter(sseTimeoutMs);

    emitter.onCompletion(
        () -> {
          log.info("SSE 연결 종료 (Completion): {}", sessionId);
          emitterMap.remove(sessionId, emitter);
        });
    emitter.onTimeout(
        () -> {
          log.warn("[SSE 타임아웃] SSE 연결 타임아웃 sessionId={}", sessionId);
          emitterMap.remove(sessionId, emitter);
          sseTimeoutCounter.increment();
        });
    emitter.onError(
        (e) -> {
          log.warn("[SSE 연결 에러] SSE 연결 중 에러 발생 sessionId={}", sessionId);
          emitterMap.remove(sessionId, emitter);
        });
    return emitter;
  }

  @Override
  public void sendCreatedMessageWithId(String sessionId, String eventId, Object data) {
    safeSend(
        sessionId,
        SseEmitter.event().id(eventId).name("created").data(data),
        "[SSE 전송 실패] 클라이언트에게 데이터 전송 중 에러 발생");
  }

  @Override
  public void sendFinishWithError(String sessionId, String message) {
    safeSend(sessionId, SseEmitter.event().name("error-finish").data(message), null);
  }

  @Override
  public void sendComplete(String sessionId) {
    safeSend(sessionId, SseEmitter.event().name("complete").data("complete"), null);
  }

  /**
   * 세션에 연결된 emitter로 이벤트를 전송한다. emitter가 없으면 no-op, {@link IOException} 발생 시 해당 emitter를 에러로 마감하며
   * 예외를 밖으로 전파하지 않는다.
   *
   * @param warnMessage 전송 실패 시 남길 경고 로그 (null이면 로그 생략)
   */
  private void safeSend(String sessionId, SseEmitter.SseEventBuilder event, String warnMessage) {
    SseEmitter emitter = emitterMap.get(sessionId);
    if (emitter == null) {
      return;
    }
    try {
      emitter.send(event);
    } catch (IOException e) {
      if (warnMessage != null) {
        log.warn(warnMessage, e);
      }
      emitter.completeWithError(e);
    }
  }
}
