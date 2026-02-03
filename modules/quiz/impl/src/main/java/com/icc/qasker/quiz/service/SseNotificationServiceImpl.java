package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
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

    @Override
    public SseEmitter createSseEmitter(String sessionID) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
        if (circuitBreaker.getState() == State.OPEN) {
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }

        SseEmitter oldEmitter = emitterMap.get(sessionID);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }

        SseEmitter emitter = new SseEmitter(100 * 1000L);
        emitterMap.put(sessionID, emitter);

        emitter.onCompletion(() -> emitterMap.remove(sessionID));
        emitter.onTimeout(() -> emitterMap.remove(sessionID));
        emitter.onError((e) -> emitterMap.remove(sessionID));
        return emitter;
    }

    @Override
    public void sendToClient(String sessionID, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(sessionID);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitter.complete();
                log.error("클라이언트에게 전송 중 에러 발생: {}", e.getMessage());
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
                sseEmitter.send(SseEmitter.event().name("complete"));
                sseEmitter.complete();
            } catch (IOException ignored) {
            }
        }
    }
}
