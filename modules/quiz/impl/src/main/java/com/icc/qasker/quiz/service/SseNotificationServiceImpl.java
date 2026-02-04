package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.SseNotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@Slf4j
@Service
@AllArgsConstructor
public class SseNotificationServiceImpl implements SseNotificationService {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final long TIMEOUT = 100 * 1000L;

    @Override
    public SseEmitter createSseEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        emitter.onCompletion(() -> emitterMap.remove(sessionId, emitter));
        emitter.onTimeout(() -> emitterMap.remove(sessionId, emitter));
        emitter.onError((e) -> {
            log.error("SSE 연결 중 에러 발생 (Session: {}): {}", sessionId, e.getMessage());
            emitterMap.remove(sessionId, emitter);
        });

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
        if (circuitBreaker.getState() == State.OPEN) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
                emitter.complete();
            } catch (IOException ignored) {
            }
            return emitter;
        }

        SseEmitter oldEmitter = emitterMap.put(sessionId, emitter);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }
        sendToClient(sessionId, "connect", "hello");

        return emitter;
    }

    @Override
    public void sendToClient(String sessionID, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(sessionID);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.error("클라이언트에게 전송 중 에러 발생: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }


    @Override
    public void sendToClient(String sessionID, String eventId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(sessionID);
        if (emitter != null) {
            try {
                SseEventBuilder eventBuilder = SseEmitter
                    .event()
                    .id(eventId)
                    .name(eventName)
                    .data(data);
                emitter.send(eventBuilder);
            } catch (IOException e) {
                emitter.complete();
                log.error("클라이언트에게 전송 중 에러 발생: {} 사유: {}", data, e.getMessage());
            }
        }
    }

    @Override
    public void complete(String sessionID) {
        SseEmitter sseEmitter = emitterMap.get(sessionID);
        if (sseEmitter != null) {
            try {
                SseEventBuilder eventBuilder = SseEmitter
                    .event()
                    .name("complete");
                sseEmitter.send(eventBuilder);
                sseEmitter.complete();
            } catch (IOException ignored) {
            }
        }
    }
}
