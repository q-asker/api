package com.icc.qasker.quiz.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;
import java.util.List;

public final class ProblemMapper {

    public static Problem fromResponse(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setNumber(quizDto.getNumber());
        problem.setId(problemId);
        problem.setTitle(quizDto.getTitle());

        problem.setProblemSet(problemSet);

        problem.setSelections(
            (quizDto.getSelections() == null ? List.<QuizGeneratedFromAI.SelectionsOfAI>of()
                : quizDto.getSelections()).stream()
                .map(selDto -> SelectionMapper.fromResponse(selDto, problem))
                .collect(toList())
        );
        problem.setExplanation(Explanation.of(quizDto.getExplanation(), problem));

        problem.setReferencedPages(
            (quizDto.getReferencedPages() == null ? List.<Integer>of()
                : quizDto.getReferencedPages()).stream()
                .map(page -> ReferencedPage.of(page, problem))
                .collect(toList())
        );

        return problem;
    }
}
