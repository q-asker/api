package com.icc.qasker.ai.properties;

import com.icc.qasker.ai.ChunkProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.ai")
public class QAskerAiProperties {

  /** Gemini Chat API 타임아웃 (ms) */
  private int chatTimeoutMs = 90_000;

  /** 선택지 균등화에 사용할 모델 (미설정 시 기본 모델 사용) */
  private String equalizationModel;

  /** 청크 분할 설정 */
  private Chunk chunk = new Chunk();

  /** Gemini File REST Client 설정 */
  private FileClient fileClient = new FileClient();

  @Getter
  @Setter
  public static class Chunk implements ChunkProperties {

    /** A/B 테스트 변형 목록 */
    private List<Integer> maxCountVariants = List.of(10);

    private AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** 변형 목록에서 순차적으로 하나를 선택한다 (라운드로빈). */
    public int pickMaxCount() {
      int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % maxCountVariants.size());
      return maxCountVariants.get(index);
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
