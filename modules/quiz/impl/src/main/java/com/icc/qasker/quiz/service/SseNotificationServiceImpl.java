package com.icc.qasker.quiz.service;

import static com.icc.qasker.global.error.ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR;

import com.icc.qasker.quiz.SseNotificationService;
import com.icc.qasker.quiz.infra.SseEmitterFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationServiceImpl implements SseNotificationService {

    private final static long TIMEOUT = 300 * 1000L;
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public SseEmitter createSseEmitter(String sessionId) {
        SseEmitter emitter = getSseEmitter(sessionId);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServer");
        if (circuitBreaker.getState() == State.OPEN) {
            emitterMap.put(sessionId, emitter);
            finishWithError(sessionId, AI_SERVER_COMMUNICATION_ERROR.getMessage());
            return emitter;
        }

        // 구 emitter로 complete 메시지 보냄
        complete(sessionId);
        // 새 emitter로 덮어 씌움
        emitterMap.put(sessionId, emitter);
        sendCreatedMessageWithId(sessionId, "connect", "hello");

        return emitter;
    }

    private @NonNull SseEmitter getSseEmitter(String sessionId) {
        SseEmitter emitter = SseEmitterFactory.createThreadSafeEmitter(TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료 (Completion): {}", sessionId);
            emitterMap.remove(sessionId, emitter);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 연결 타임아웃 (Timeout): {}", sessionId);
            emitterMap.remove(sessionId, emitter);
        });
        emitter.onError((e) -> {
            log.error("SSE 연결 에러 발생 (Session: {}): {}", sessionId, e.getMessage());
            emitterMap.remove(sessionId, emitter);
        });
        return emitter;
    }

    @Override
    public void sendCreatedMessageWithId(String sessionID, String eventId, Object data) {
        SseEmitter emitter = emitterMap.get(sessionID);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter
                    .event()
                    .id(eventId)
                    .name("created")
                    .data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
                log.error("클라이언트에게 전송 중 에러 발생: {} 사유: {}", data, e.getMessage());
            }
        }
    }

    @Override
    public void finishWithError(String sessionId, String message) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter
                    .event()
                    .name("error-finish")
                    .data(message));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    public void complete(String sessionId) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter
                    .event()
                    .name("complete")
                    .data("complete"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
