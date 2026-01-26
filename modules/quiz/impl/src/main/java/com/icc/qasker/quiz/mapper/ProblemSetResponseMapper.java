package com.icc.qasker.quiz.mapper;

import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe.SelectionsForFE;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.Selection;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public final class ProblemSetResponseMapper {

    private final HashUtil hashUtil;

    public QuizForFe fromEntity(Problem problem) {
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

    public ProblemSetResponse fromEntity(ProblemSet problemSet) {
        List<QuizForFe> quizzes = problemSet.getProblems().stream()
            .map(this::fromEntity)
            .toList();

        return new ProblemSetResponse(
            hashUtil.encode(problemSet.getId()),
            quizzes.size(),
            quizzes
        );
    }
}