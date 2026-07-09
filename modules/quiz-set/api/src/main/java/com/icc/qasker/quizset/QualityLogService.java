package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.QualityLogEntry;

/**
 * 문항 품질 로그(problem_quality_log) 영속화 계약. 문항 1:1로 첫 생성본(v1)을 write-once로 기록하고, 재생성된 개선본(v2)을 부착한다.
 * 학습자 서빙(problem)과 분리된 저장이다.
 */
public interface QualityLogService {

  /** 첫 생성본(v1)을 기록한다(problemSetId+number 유일, write-once — 이미 있으면 유지). */
  void upsertV1(QualityLogEntry entry);

  /** 재생성된 개선본(v2)의 질문·해설을 해당 품질 로그 행에 부착한다(행이 없으면 무시). */
  void attachV2(Long problemSetId, int number, String v2QuestionJson, String v2Explanation);
}
