package com.icc.qasker.ai.properties;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 문항 품질 검증 설정(q-asker.ai.quality). 검증 모델·토큰 단가와 적용 검증 항목·엄격도(criteria)를 노출한다. criteria는 항목명→엄격도
 * (strict/normal/off) 맵으로, 운영자가 yml에서 조절한다(FR-011).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "q-asker.ai.quality")
public class QualityProperties {

  /** 검증 모델(flash-lite 계열). per-call로 지정된다. */
  private String verifyModel;

  /** 검증 모델 입력 토큰 단가 (USD per 1M). */
  private double priceInputPer1m;

  /** Pass 1 원문 대조 캐시 읽기 단가 (USD per 1M). */
  private double priceCacheReadPer1m;

  /** 검증 모델 출력 토큰 단가 (USD per 1M). */
  private double priceOutputPer1m;

  /** 적용 검증 항목 → 엄격도(strict/normal/off). */
  private Map<String, String> criteria = new LinkedHashMap<>();
}
