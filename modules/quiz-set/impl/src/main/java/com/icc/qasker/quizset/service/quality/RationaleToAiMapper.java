package com.icc.qasker.quizset.service.quality;

import com.icc.qasker.ai.dto.AIRationale;
import com.icc.qasker.quizset.entity.Rationale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 엔티티 Rationale → 검증기 입력 AIRationale 변환(Pass 2 재검토용). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RationaleToAiMapper {

  static AIRationale toDto(Rationale r) {
    if (r == null) {
      return null;
    }
    AIRationale.SourceAnchor anchor =
        r.sourceAnchor() == null
            ? null
            : new AIRationale.SourceAnchor(
                r.sourceAnchor().page(), r.sourceAnchor().section(), r.sourceAnchor().quote());
    AIRationale.SelfChecks checks =
        r.selfChecks() == null
            ? null
            : new AIRationale.SelfChecks(
                r.selfChecks().singleCorrectAnswer(),
                r.selfChecks().answerGroundedInSource(),
                r.selfChecks().distractorsPlausible(),
                r.selfChecks().noOutsideKnowledge());
    return new AIRationale(
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
