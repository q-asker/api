package com.icc.qasker.quiz.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.sse")
public class QAskerSseProperties {

  /** SSE 연결 타임아웃 (ms) */
  private long timeoutMs = 300_000;
}
