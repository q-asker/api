package com.icc.qasker.quiz.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;

public final class ProblemMapper {

//    public static ProblemSet fromResponse(ProblemSetGeneratedEvent aiResponse) {
//        return fromResponse(aiResponse, null);
//    }
//
//    public static ProblemSet fromResponse(ProblemSetGeneratedEvent aiResponse, String userId) {
//        if (aiResponse == null || aiResponse.getQuiz() == null) {
//            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
//        }
//        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
//        problemSet.setProblems(aiResponse.getQuiz().stream()
//            .map(quizDto -> fromQuizDto(quizDto, problemSet))
//            .toList());
//        return problemSet;
//    }

    public static Problem fromResponse(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setNumber(quizDto.getNumber());
        problem.setId(problemId);
        problem.setTitle(quizDto.getTitle());

        problem.setProblemSet(problemSet);

        problem.setSelections(
            quizDto.getSelections().stream()
                .map(selDto -> SelectionMapper.fromResponse(selDto, problem))
                .collect(toList())
        );
        problem.setExplanation(Explanation.of(quizDto.getExplanation(), problem));

        problem.setReferencedPages(
            quizDto.getReferencedPages().stream()
                .map(page -> ReferencedPage.of(page, problem))
                .collect(toList())
        );

        return problem;
    }
}
