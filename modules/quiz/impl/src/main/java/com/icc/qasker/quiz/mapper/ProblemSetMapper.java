package com.icc.qasker.quiz.mapper;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;
import java.util.List;

public final class ProblemSetMapper {

    public static ProblemSet fromResponse(AiGenerationResponse aiResponse) {
        if (aiResponse == null || aiResponse.getQuiz() == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        ProblemSet problemSet = new ProblemSet();
        problemSet.setTitle(aiResponse.getTitle());

        List<Problem> problems = aiResponse.getQuiz().stream()
            .map(quizDto -> fromAiDto(quizDto, problemSet))
            .toList();

        problemSet.setProblems(problems);

        return problemSet;

    }

    private static Problem fromAiDto(QuizGeneratedByAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setNumber(quizDto.getNumber());
        problem.setId(problemId);
        problem.setTitle(quizDto.getTitle());

        problem.setProblemSet(problemSet);

        problem.setSelections(
            quizDto.getSelections().stream()
                .map(selDto -> SelectionMapper.fromResponse(selDto, problem))
                .toList()
        );

        problem.setExplanation(Explanation.of(quizDto.getExplanation(), problem));

        problem.setReferencedPages(
            quizDto.getReferencedPages().stream()
                .map(page -> ReferencedPage.of(page, problem))
                .toList()
        );

        return problem;
    }
}
