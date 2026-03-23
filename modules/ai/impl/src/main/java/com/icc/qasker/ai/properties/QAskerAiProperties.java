package com.icc.qasker.ai.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.ai")
public class QAskerAiProperties {

  /** Gemini Chat API 타임아웃 (ms) */
  private int chatTimeoutMs = 90_000;

  /** Gemini File REST Client 설정 */
  private FileClient fileClient = new FileClient();

  @Getter
  @Setter
  public static class FileClient {

    /** Gemini File API 베이스 URL */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 5_000;

    /** 읽기 타임아웃 (ms) */
    private int readTimeoutMs = 30_000;
  }
}
