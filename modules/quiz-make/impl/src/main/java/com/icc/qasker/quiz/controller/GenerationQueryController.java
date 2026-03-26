package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.GenerationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Generation", description = "문제 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationQueryController {

  private final GenerationQueryService generationQueryService;

  @Operation(summary = "제공받은 세션키로 문제 전송을 위한 emitter를 생성한다")
  @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribeToGeneration(
      @PathVariable @UUID String sessionId,
      @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "")
          String lastEventId) {
    return generationQueryService.subscribe(sessionId, lastEventId);
  }
}
