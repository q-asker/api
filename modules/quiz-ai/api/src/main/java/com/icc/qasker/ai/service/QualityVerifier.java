package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;

/**
 * AI 문항 품질 검증기(api 계약). 경량 모델로 문항의 필수 항목(정답↔근거·단일 정답·사용자 지시 반영·구성전략 부합 등)을 이진 판정한다. 구현은
 * quiz-ai/impl에 위치하며, 재검토 서비스(quiz-set/impl)는 이 인터페이스에만 의존한다(헌법 III).
 */
public interface QualityVerifier {

  QualityVerdict verify(QualityVerificationRequest request);
}
