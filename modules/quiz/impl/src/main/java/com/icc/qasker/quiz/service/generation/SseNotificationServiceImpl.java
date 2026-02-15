package com.icc.qasker.quiz.service.generation;

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

    private static final long TIMEOUT = 300 * 1000L;
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;

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
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter
                    .event()
                    .name("connected")
                    .data("hello"));
            } catch (IOException e) {
                log.error("클라이언트에게 연결 중 에러 사유: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    private @NonNull SseEmitter initSseEmitter(String sessionId) {
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
    public void sendCreatedMessageWithId(String sessionId, String eventId, Object data) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter
                    .event()
                    .id(eventId)
                    .name("created")
                    .data(data));
            } catch (IOException e) {
                log.error("클라이언트에게 전송 중 에러 발생: {} 사유: {}", data, e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    public void sendFinishWithError(String sessionId, String message) {
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
    public void sendComplete(String sessionId) {
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
