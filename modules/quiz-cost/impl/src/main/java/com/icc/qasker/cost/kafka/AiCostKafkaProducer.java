package com.icc.qasker.cost.kafka;

import com.icc.qasker.cost.config.AiCostKafkaProperties;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 비용 이벤트를 ai.cost.raw 토픽으로 발행한다. key=userId(파티션 분배), value=Outbox에 적재된 JSON 문자열(그대로 발행).
 *
 * <p>동기 발행(get)으로 성공 여부를 확정하여 MessageRelay가 Outbox 상태 전이를 안전하게 판단하도록 한다. test 프로파일은
 * KafkaAutoConfiguration을 제외하므로 이 빈을 생성하지 않는다(@Profile("!test")).
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AiCostKafkaProducer {

  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final AiCostKafkaProperties properties;

  /**
   * 이벤트를 동기 발행한다. 발행 실패 시 예외를 던져 호출자(Relay)가 Outbox를 PENDING으로 유지하게 한다.
   *
   * @param key 파티션 key (userId)
   * @param payload 발행할 JSON 문자열
   */
  public void send(String key, String payload) {
    try {
      kafkaTemplate
          .send(properties.getTopic(), key, payload)
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AI 비용 이벤트 발행 중 인터럽트: key=" + key, e);
    } catch (Exception e) {
      throw new IllegalStateException("AI 비용 이벤트 발행 실패: key=" + key, e);
    }
  }
}
