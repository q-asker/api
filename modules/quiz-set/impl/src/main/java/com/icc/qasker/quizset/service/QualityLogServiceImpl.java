package com.icc.qasker.quizset.service;

import com.icc.qasker.quizset.QualityLogService;
import com.icc.qasker.quizset.dto.QualityLogEntry;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문항 품질 로그 v1 write-once 기록·v2 부착. problem과 분리된 생성 근거 저장소를 관리한다. */
@Service
@RequiredArgsConstructor
public class QualityLogServiceImpl implements QualityLogService {

  private final ProblemQualityLogRepository repository;

  @Override
  @Transactional
  public void upsertV1(QualityLogEntry entry) {
    // write-once: 행이 있으면 v1은 최초 값을 유지한다(중복 기록 방지).
    if (repository.findByProblemSetIdAndNumber(entry.problemSetId(), entry.number()).isPresent()) {
      return;
    }
    repository.save(
        ProblemQualityLog.builder()
            .problemSetId(entry.problemSetId())
            .number(entry.number())
            .v1QuestionJson(entry.v1QuestionJson())
            .v1Explanation(entry.v1Explanation())
            .v1Feedback(entry.v1Feedback())
            .build());
  }

  @Override
  @Transactional
  public void attachV2(Long problemSetId, int number, String v2QuestionJson, String v2Explanation) {
    repository
        .findByProblemSetIdAndNumber(problemSetId, number)
        .ifPresent(row -> row.bindV2(v2QuestionJson, v2Explanation));
  }
}
