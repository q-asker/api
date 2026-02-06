package com.icc.qasker.quiz;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseNotificationService {

    SseEmitter createSseEmitter(String sessionId);

    void sendCreatedMessageWithId(String sessionId, String eventName, Object data);

    void finishWithError(String sessionId, String message);

    void complete(String sessionId);
}
