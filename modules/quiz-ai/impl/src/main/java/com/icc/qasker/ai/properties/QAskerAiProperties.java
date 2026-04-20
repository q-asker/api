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

  /** 청크 분할 설정 */
  private Chunk chunk = new Chunk();

  /** Google Cloud Storage 설정 */
  private Gcs gcs = new Gcs();

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
  public static class Gcs {

    /** GCS 버킷 이름 */
    private String bucketName;
  }
}
