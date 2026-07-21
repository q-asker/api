package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import java.util.Optional;

/**
 * AI 문항 품질 검증기(api 계약). 경량 모델로 문항의 필수 항목(정답↔근거·단일 정답·사용자 지시 반영·구성전략 부합 등)을 이진 판정한다. 구현은
 * quiz-ai/impl에 위치하며, 재검토 서비스(quiz-set/impl)는 이 인터페이스에만 의존한다(헌법 III).
 */
public interface QualityVerifier {

  QualityVerdict verify(QualityVerificationRequest request);

  /**
   * Pass 1 검증용 컨텍스트 캐시(검증 루브릭+PDF 원문 프리픽스)를 세션당 1개 생성한다. 반환된 캐시 참조를 {@link
   * QualityVerificationRequest#cacheRef()}로 넘기면 검증기가 PDF 원문과 직접 대조해 환각·출처 이탈을 잡는다. 비지원 ChatModel·최소
   * 토큰 미달 등으로 실패하면 빈 값을 반환한다(캐시 없이 원문 대조 없는 검증으로 폴백).
   */
  Optional<CacheRef> createPass1Cache(String pdfUri, String quizType, String language);

  /** {@link #createPass1Cache}로 만든 캐시를 삭제한다(세션 종료 시). cacheRef가 null이면 무시한다. */
  void deletePass1Cache(CacheRef cacheRef);
}
