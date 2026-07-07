package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.QualityLogEntry;

/**
 * 문항 품질 로그(problem_quality_log) 영속화 계약. 문항 1:1로 생성 근거·품질 판정을 upsert하고, 재생성 원본(v1)을 부착한다. 학습자
 * 서빙(problem)과 분리된 저장이다.
 */
public interface QualityLogService {

  /** 문항 품질(rationale·상태·피드백)을 upsert한다(problemSetId+number 유일). */
  void upsertQuality(QualityLogEntry entry);

  /** 재생성된 문항의 원본 미달본(v1)을 해당 품질 로그 행에 부착한다(행이 없으면 무시). */
  void attachRegenerationSource(Long problemSetId, int number, String v1Json, String v1Feedback);
}
