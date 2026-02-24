package com.icc.qasker.quiz.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AIProblemSetMapper {

    public static ProblemSetGeneratedEvent toEvent(AIProblemSet source) {
        List<QuizGeneratedFromAI> quizList = source.quiz().stream()
            .map(AIProblemSetMapper::toQuizGeneratedFromAI)
            .toList();

        ProblemSetGeneratedEvent event = new ProblemSetGeneratedEvent();
        event.setQuiz(quizList);
        return event;
    }

    private static QuizGeneratedFromAI toQuizGeneratedFromAI(AIProblem problem) {
        QuizGeneratedFromAI quiz = new QuizGeneratedFromAI();
        quiz.setNumber(problem.number());
        quiz.setTitle(problem.title());
        quiz.setExplanation(problem.explanation());
        quiz.setReferencedPages(problem.referencedPages());
        quiz.setSelections(
            problem.selections() == null
                ? List.of()
                : problem.selections().stream()
                    .map(AIProblemSetMapper::toSelectionsOfAI)
                    .toList()
        );
        return quiz;
    }

    private static SelectionsOfAI toSelectionsOfAI(AISelection selection) {
        SelectionsOfAI sel = new SelectionsOfAI();
        sel.setContent(selection.content());
        sel.setCorrect(selection.correct());
        return sel;
    }
}
