package com.icc.qasker.mapper;

import com.icc.qasker.dto.response.ProblemSetResponse;
import com.icc.qasker.dto.response.ProblemSetResponse.QuizForFe;
import com.icc.qasker.dto.response.ProblemSetResponse.QuizForFe.SelectionsForFE;
import com.icc.qasker.entity.Problem;
import com.icc.qasker.entity.ProblemSet;
import com.icc.qasker.entity.Selection;
import java.util.List;
import java.util.stream.IntStream;

public final class ProblemSetResponseMapper {

    public static ProblemSetResponse fromEntity(ProblemSet problemSet) {
        List<QuizForFe> quizzes = problemSet.getProblems().stream()
            .map(ProblemSetResponseMapper::fromEntity)
            .toList();

        return new ProblemSetResponse(
            problemSet.getTitle(),
            quizzes
        );
    }

    private static QuizForFe fromEntity(Problem problem) {
        List<SelectionsForFE> selections = IntStream
            .range(0, problem.getSelections().size())
            .mapToObj(i -> {
                Selection selection = problem.getSelections().get(i);
                return new SelectionsForFE(
                    i + 1,
                    selection.getContent(),
                    selection.isCorrect()
                );
            })
            .toList();

        return new QuizForFe(
            problem.getId().getNumber(),
            problem.getTitle(),
            0,
            false,
            selections
        );
    }
}