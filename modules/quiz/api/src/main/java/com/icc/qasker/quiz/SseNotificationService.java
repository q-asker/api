package com.icc.qasker.quiz;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseNotificationService {

    SseEmitter createSseEmitter(String sessionID);

    void sendToClient(String sessionID, String eventName, Object data);

    void sendToClient(String sessionID, String eventId, String eventName, Object data);

    void complete(String sessionID);
}
