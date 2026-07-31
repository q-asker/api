package com.icc.qasker.quizmake.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.quizmake.text.MarkdownNormalizer;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
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
    // 저장 보편 초크포인트: 실제 생성·mock·fast-serve·전 타입이 모두 여기로 수렴한다. 글루된 표 개행을 결정론적으로 복원한다(계약 §7.3-2).
    quiz.setTitle(MarkdownNormalizer.normalize(problem.content()));
    quiz.setBloomsLevel(problem.bloomsLevel());
    quiz.setReferencedPages(problem.referencedPages());
    quiz.setAppliedInstruction(problem.appliedInstruction());
    quiz.setSelections(
        problem.selections() == null
            ? List.of()
            : problem.selections().stream().map(AIProblemSetMapper::toSelectionsOfAI).toList());
    return quiz;
  }

  private static SelectionsOfAI toSelectionsOfAI(AISelection selection) {
    SelectionsOfAI sel = new SelectionsOfAI();
    sel.setContent(MarkdownNormalizer.normalize(selection.content()));
    sel.setExplanation(MarkdownNormalizer.normalize(selection.explanation()));
    sel.setCorrect(selection.correct());
    sel.setAcceptedAnswers(selection.acceptedAnswers());
    return sel;
  }
}
