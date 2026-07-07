package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIRationale;
import com.icc.qasker.ai.structure.GeminiRationale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** GeminiRationale(structure) → AIRationale(api DTO) 변환. AI가 rationale을 산출하지 않으면 null을 전파한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeminiRationaleMapper {

  public static AIRationale toDto(GeminiRationale r) {
    if (r == null) {
      return null;
    }
    return new AIRationale(
        toSourceAnchor(r.sourceAnchor()),
        r.learningObjective(),
        r.bloomLevel(),
        r.difficultyEstimate(),
        r.constructionStrategy(),
        r.instructionApplication(),
        r.confidence(),
        toSelfChecks(r.selfChecks()),
        r.modelAnswerBasis(),
        r.rubricConsistency());
  }

  private static AIRationale.SourceAnchor toSourceAnchor(GeminiRationale.SourceAnchor s) {
    if (s == null) {
      return null;
    }
    return new AIRationale.SourceAnchor(s.page(), s.section(), s.quote());
  }

  private static AIRationale.SelfChecks toSelfChecks(GeminiRationale.SelfChecks c) {
    if (c == null) {
      return null;
    }
    return new AIRationale.SelfChecks(
        c.singleCorrectAnswer(),
        c.answerGroundedInSource(),
        c.distractorsPlausible(),
        c.noOutsideKnowledge());
  }
}
