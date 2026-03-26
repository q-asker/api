package com.icc.qasker.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.async")
public class QAskerAsyncProperties {

  /** 비동기 태스크 종료 대기 타임아웃 (ms) */
  private long taskTerminationTimeoutMs = 10_000;
}
