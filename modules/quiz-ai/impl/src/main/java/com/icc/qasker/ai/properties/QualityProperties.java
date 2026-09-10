package com.icc.qasker.ai.properties;

import com.icc.qasker.ai.service.quality.prompt.QualityCriterion;
import com.icc.qasker.ai.service.quality.prompt.Strictness;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 문항 품질 검증 설정(q-asker.ai.quality). 검증 모델·토큰 단가와 적용 검증 항목·엄격도(criteria)를 노출한다. criteria는 항목→엄격도
 * (strict/normal/off) 맵으로, 운영자가 yml에서 조절한다(FR-011). 키·값이 모두 enum이라 yml 오타는 기동 시 바인딩 실패로 드러난다.
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

  /** 적용 검증 항목 → 엄격도. yml 기재 순서가 프롬프트에 실리는 순서다. */
  private Map<QualityCriterion, Strictness> criteria = new LinkedHashMap<>();
}
