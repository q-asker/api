package com.icc.qasker.quizmake;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationQueryService {

  SseEmitter subscribe(String sessionId, String lastEventId);
}
