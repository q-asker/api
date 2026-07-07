package com.icc.qasker.quizset.service;

import com.icc.qasker.quizset.QualityLogService;
import com.icc.qasker.quizset.dto.QualityLogEntry;
import com.icc.qasker.quizset.dto.airesponse.RationaleOfAI;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.QualityStatus;
import com.icc.qasker.quizset.entity.Rationale;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문항 품질 로그 upsert·재생성 원본 부착. problem과 분리된 품질 상태 저장소를 관리한다. */
@Service
@RequiredArgsConstructor
public class QualityLogServiceImpl implements QualityLogService {

  private final ProblemQualityLogRepository repository;

  @Override
  @Transactional
  public void upsertQuality(QualityLogEntry entry) {
    QualityStatus status =
        entry.qualityStatus() == null ? null : QualityStatus.valueOf(entry.qualityStatus());
    Rationale rationale = toRationale(entry.rationale());

    repository
        .findByProblemSetIdAndNumber(entry.problemSetId(), entry.number())
        .ifPresentOrElse(
            row -> {
              row.bindRationale(rationale);
              row.applyVerdict(status, entry.feedback());
            },
            () ->
                repository.save(
                    ProblemQualityLog.builder()
                        .problemSetId(entry.problemSetId())
                        .number(entry.number())
                        .rationale(rationale)
                        .qualityStatus(status)
                        .feedback(entry.feedback())
                        .build()));
  }

  @Override
  @Transactional
  public void attachRegenerationSource(
      Long problemSetId, int number, String v1Json, String v1Feedback) {
    repository
        .findByProblemSetIdAndNumber(problemSetId, number)
        .ifPresent(row -> row.bindV1(v1Json, v1Feedback));
  }

  private static Rationale toRationale(RationaleOfAI r) {
    if (r == null) {
      return null;
    }
    Rationale.SourceAnchor anchor =
        r.sourceAnchor() == null
            ? null
            : new Rationale.SourceAnchor(
                r.sourceAnchor().page(), r.sourceAnchor().section(), r.sourceAnchor().quote());
    Rationale.SelfChecks checks =
        r.selfChecks() == null
            ? null
            : new Rationale.SelfChecks(
                r.selfChecks().singleCorrectAnswer(),
                r.selfChecks().answerGroundedInSource(),
                r.selfChecks().distractorsPlausible(),
                r.selfChecks().noOutsideKnowledge());
    return new Rationale(
        anchor,
        r.learningObjective(),
        r.bloomLevel(),
        r.difficultyEstimate(),
        r.constructionStrategy(),
        r.instructionApplication(),
        r.confidence(),
        checks,
        r.modelAnswerBasis(),
        r.rubricConsistency());
  }
}
