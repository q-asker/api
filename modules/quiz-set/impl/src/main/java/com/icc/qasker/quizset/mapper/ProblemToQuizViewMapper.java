package com.icc.qasker.quizset.mapper;

import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.view.QuizView;
import com.icc.qasker.quizset.view.QuizView.SelectionView;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemToQuizViewMapper {

  public static QuizView toQuizView(Problem problem) {
    List<SelectionView> selections =
        QuizMappingSupport.mapSelections(
            problem.getSelections(),
            (id, sel) -> new SelectionView(id, sel.content(), sel.correct()));

    return new QuizView(
        problem.getId().getNumber(),
        problem.getTitle(),
        QuizMappingSupport.UNANSWERED_USER_ANSWER,
        QuizMappingSupport.UNCHECKED,
        selections,
        problem.getAppliedInstruction());
  }
}
