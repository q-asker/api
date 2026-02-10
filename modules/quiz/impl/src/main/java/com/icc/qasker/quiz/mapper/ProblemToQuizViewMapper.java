package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;
import com.icc.qasker.quiz.view.QuizView;
import com.icc.qasker.quiz.view.QuizView.SelectionView;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemToQuizViewMapper {

    public static QuizView toQuizView(Problem problem) {
        List<SelectionView> selections = IntStream
            .range(0, problem.getSelections().size())
            .mapToObj(i -> {
                Selection selection = problem.getSelections().get(i);
                return new SelectionView(
                    i + 1,
                    selection.getContent(),
                    selection.isCorrect()
                );
            })
            .toList();

        return new QuizView(
            problem.getId().getNumber(),
            problem.getTitle(),
            0,
            false,
            selections
        );
    }
}
