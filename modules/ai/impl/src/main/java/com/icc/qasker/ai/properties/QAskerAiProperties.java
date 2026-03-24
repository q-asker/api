package com.icc.qasker.ai.properties;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.ai")
public class QAskerAiProperties {

  /** Gemini Chat API 타임아웃 (ms) */
  private int chatTimeoutMs = 90_000;

  /** 청크 분할 설정 */
  private Chunk chunk = new Chunk();

  /** Gemini File REST Client 설정 */
  private FileClient fileClient = new FileClient();

  @Getter
  @Setter
  public static class Chunk {

    /** A/B 테스트 변형 목록 — 요청마다 랜덤 선택 */
    private List<Integer> maxCountVariants = List.of(10);

    /** 변형 목록에서 랜덤으로 하나를 선택한다. */
    public int pickMaxCount() {
      List<Integer> variants = maxCountVariants;
      return variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
    }
  }

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
