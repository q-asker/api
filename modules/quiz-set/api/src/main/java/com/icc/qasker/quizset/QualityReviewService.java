package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.QualityReviewResult;
import java.util.List;
import java.util.Optional;

/**
 * 승인 기반 품질 재검토(Pass 2) 서비스 계약. 저장된 세트의 rationale 보유 문항을 원문 없이 재검증하고, 미달 문항에만 품질 상태·피드백을 마킹한다(통과분
 * 무변경 → dirty tracking으로 미달 subset만 UPDATE). 레거시(rationale=NULL) 세트는 대상에서 제외한다(FR-014).
 */
public interface QualityReviewService {

  /** 단일 세트를 재검토한다. rationale 보유 문항이 없으면 예외(단건 400). */
  QualityReviewResult review(Long problemSetId);

  /** 여러 세트를 재검토한다. 레거시 세트는 건너뛰고 결과에서 제외한다(일괄 스킵). */
  List<QualityReviewResult> reviewBulk(List<Long> problemSetIds);

  /** 단일 세트 재검토를 비동기로 트리거한다(운영자 엔드포인트 — 즉시 202 반환용). */
  void submitReview(Long problemSetId);

  /** 여러 세트 재검토를 비동기로 트리거한다. */
  void submitReviewBulk(List<Long> problemSetIds);

  /** 최근 재검토 결과를 조회한다. */
  Optional<QualityReviewResult> latestResult(Long problemSetId);
}
