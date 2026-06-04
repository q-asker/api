package com.icc.qasker.cost.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 비용 발행 토픽/릴레이 설정. application.yml의 ai.cost.kafka 프리픽스와 매핑된다(@ConfigurationPropertiesScan 자동
 * 등록).
 */
@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "ai.cost.kafka")
public class AiCostKafkaProperties {

  /** 발행 토픽명 (ai.cost.raw) */
  private final String topic;

  /** MessageRelay 1회 폴링 배치 크기 */
  private final int relayBatchSize;
}
