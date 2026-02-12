package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;

public final class SelectionMapper {

    public static Selection fromResponse(SelectionsOfAI dto, Problem problem) {
        return Selection.builder()
            .content(dto.getContent())
            .correct(dto.isCorrect())
            .problem(problem)
            .build();
    }

}
