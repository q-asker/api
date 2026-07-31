package com.icc.qasker.quizmake.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.sse")
public class QAskerSseProperties {

  /** SSE 연결 타임아웃 (ms) */
  private long timeoutMs = 300_000;

  /**
   * SSE heartbeat(keep-alive comment) 전송 주기 (ms). 긴 TTFQ 무음 구간에 중간 프록시(Cloudflare 등)가 idle 스트림을 절단해
   * 클라이언트가 재연결하는 것을 예방한다. EventSource는 comment 라인을 무시하므로 클라이언트 코드 변경은 없다.
   */
  private long heartbeatIntervalMs = 15_000;
}
