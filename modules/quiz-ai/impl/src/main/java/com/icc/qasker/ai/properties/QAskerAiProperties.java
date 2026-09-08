package com.icc.qasker.ai.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.ai")
public class QAskerAiProperties {

  /** 컨텍스트 캐시 TTL — q-asker.ai.cache-ttl. 한 세트 생성 세션(인터리빙 전체)을 커버하고, 종료 시 명시적으로 삭제한다. */
  private Duration cacheTtl;

  /**
   * 즉석 서빙 문항 수 — q-asker.ai.fast-serve-count. 처음 N문항은 생성 게이트(Pass 1) 검증 없이 파싱 즉시 저장·전송한다(0=비활성).
   * 품질은 사후 Pass 2 재검토가 판정한다.
   */
  private int fastServeCount;

  /** Gemini HTTP 클라이언트 시한 계층 (connect < read < call) */
  private GeminiHttp geminiHttp = new GeminiHttp();

  /** 청크 분할 설정 */
  private Chunk chunk = new Chunk();

  /** 생성 세션 내부 동시성 상한 */
  private Concurrency concurrency = new Concurrency();

  /** Google Cloud Storage 설정 */
  private Gcs gcs = new Gcs();

  @Getter
  @Setter
  public static class Chunk {

    /** 한 Gemini 호출당 요청할 최대 문항 수 — q-asker.ai.chunk.chunk-size. requestedCount가 이보다 크면 청크로 분할한다. */
    private int chunkSize;
  }

  /**
   * 생성 세션 한 건이 동시에 띄우는 작업 수의 상한. 두 값 모두 quizCount 최대 30({@code
   * GenerationRequest.DEFAULT_ALLOWED_COUNTS})을 덮도록 잡혀 있어, 기본값에서는 세마포어가 실제로 막지 않고 한 라운드에 소진된다.
   * Vertex 는 generateContent 에 동시성 쿼터를 두지 않으므로(제한은 RPM·TPM) 작업 개수가 곧 상한이다.
   *
   * <p>TODO: 이 값은 <b>세션 1건</b> 기준이다. 세마포어·익스큐터가 Session 인스턴스 필드라 동시 생성 요청 수만큼 곱해진다(3요청 → 최대 90). 전역
   * 상한이 없으므로 동시 생성이 늘면 제한 계층을 추가해야 한다.
   */
  @Getter
  @Setter
  public static class Concurrency {

    /** 비동기 생성 게이트 검증의 동시 실행 상한 — q-asker.ai.concurrency.verify. */
    private int verify = 30;

    /** 보류 문항 재생성의 동시 실행 상한 — q-asker.ai.concurrency.regenerate. */
    private int regenerate = 30;
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

    /** 청크 HTTP 호출 총 시한. 근거: 청크 소요 관측 최대 274.3s + 여유 ~9%. */
    private Duration callTimeout = Duration.ofSeconds(300);
  }
}
