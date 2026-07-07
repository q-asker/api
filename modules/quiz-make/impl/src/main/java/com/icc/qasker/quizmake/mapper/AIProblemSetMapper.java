package com.icc.qasker.quizmake.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AIRationale;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.airesponse.RationaleOfAI;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AIProblemSetMapper {

  public static ProblemSetGeneratedEvent toEvent(AIProblemSet source) {
    List<QuizGeneratedFromAI> quizList =
        source.quiz().stream().map(AIProblemSetMapper::toQuizGeneratedFromAI).toList();

    ProblemSetGeneratedEvent event = new ProblemSetGeneratedEvent();
    event.setQuiz(quizList);
    return event;
  }

  /** 단건 AIProblem을 전송/영속용 DTO로 변환한다(배치 인터리빙 Phase 1 저장). */
  public static QuizGeneratedFromAI toQuiz(AIProblem problem) {
    return toQuizGeneratedFromAI(problem);
  }

  private static QuizGeneratedFromAI toQuizGeneratedFromAI(AIProblem problem) {
    QuizGeneratedFromAI quiz = new QuizGeneratedFromAI();
    quiz.setTitle(problem.content());
    quiz.setBloomsLevel(problem.bloomsLevel());
    quiz.setReferencedPages(problem.referencedPages());
    quiz.setAppliedInstruction(problem.appliedInstruction());
    quiz.setSelections(
        problem.selections() == null
            ? List.of()
            : problem.selections().stream().map(AIProblemSetMapper::toSelectionsOfAI).toList());
    return quiz;
  }

  /** AIRationale → 품질 로그 전송용 RationaleOfAI. 품질 로그 upsert 시 sink가 사용한다. */
  public static RationaleOfAI toRationaleOfAI(AIRationale r) {
    if (r == null) {
      return null;
    }
    RationaleOfAI.SourceAnchor anchor =
        r.sourceAnchor() == null
            ? null
            : new RationaleOfAI.SourceAnchor(
                r.sourceAnchor().page(), r.sourceAnchor().section(), r.sourceAnchor().quote());
    RationaleOfAI.SelfChecks checks =
        r.selfChecks() == null
            ? null
            : new RationaleOfAI.SelfChecks(
                r.selfChecks().singleCorrectAnswer(),
                r.selfChecks().answerGroundedInSource(),
                r.selfChecks().distractorsPlausible(),
                r.selfChecks().noOutsideKnowledge());
    return new RationaleOfAI(
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

  private static SelectionsOfAI toSelectionsOfAI(AISelection selection) {
    SelectionsOfAI sel = new SelectionsOfAI();
    sel.setContent(selection.content());
    sel.setExplanation(selection.explanation());
    sel.setCorrect(selection.correct());
    return sel;
  }
}
