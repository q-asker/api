package com.icc.qasker.ai.properties;

import com.icc.qasker.ai.ChunkProperties;
import java.time.Duration;
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

  /** Gemini HTTP 클라이언트 시한 계층 (connect < read < call) */
  private GeminiHttp geminiHttp = new GeminiHttp();

  /** 청크 분할 설정 */
  private Chunk chunk = new Chunk();

  /** Google Cloud Storage 설정 */
  private Gcs gcs = new Gcs();

  @Getter
  @Setter
  public static class Chunk implements ChunkProperties {

    /** A/B 테스트 변형 목록 */
    private List<Integer> maxCountVariants = List.of(10);

    /** 한 Gemini 호출당 요청할 최대 문항 수. requestedCount가 이보다 크면 청크로 분할한다. */
    private int chunkSize = 10;

    private AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** 변형 목록에서 순차적으로 하나를 선택한다 (라운드로빈). */
    public int pickMaxCount() {
      int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % maxCountVariants.size());
      return maxCountVariants.get(index);
    }

    /**
     * 요청 문항 수를 chunkSize 단위 청크로 분할한다. 마지막 청크는 잔여분만큼.
     *
     * <p>예: requestedCount=25, chunkSize=10 → [10, 10, 5]
     */
    public List<Integer> planChunks(int requestedCount) {
      if (requestedCount <= 0) return List.of();
      int size = Math.max(1, chunkSize);
      java.util.ArrayList<Integer> plan = new java.util.ArrayList<>();
      int remaining = requestedCount;
      while (remaining > 0) {
        int next = Math.min(size, remaining);
        plan.add(next);
        remaining -= next;
      }
      return java.util.Collections.unmodifiableList(plan);
    }
  }

  @Getter
  @Setter
  public static class Gcs {

    /** GCS 버킷 이름 */
    private String bucketName;
  }

  /**
   * Gemini 스트리밍 HTTP 시한 계층. 각 임계는 "관측 성공 최대 +10%" 레시피로 산출 — 그 아래로 내리면 실존했던 성공을 자르는 오탐이 된다 (측정
   * 2026-07-11, 78일 스냅샷).
   */
  @Getter
  @Setter
  public static class GeminiHttp {

    /** 연결 수립 시한. 정상 연결은 수백 ms — 초과는 가장 깨끗한 인프라 장애 신호. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 무음 갭 시한: 바이트가 이 시간 동안 안 오면 절단. 근거: 첫 문제 도착(TTFQ) 관측 최대 134.9s. */
    private Duration readTimeout = Duration.ofSeconds(150);

    /** 청크 HTTP 호출 총 시한. 근거: 청크 소요 관측 최대 274.3s + 여유. */
    private Duration callTimeout = Duration.ofSeconds(350);
  }
}
