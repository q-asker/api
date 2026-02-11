package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI.SelectionsOfAi;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;

public final class SelectionMapper {

    public static Selection fromResponse(SelectionsOfAi selDto, Problem problem) {
        if (selDto == null) {
            return null;
        }
        Selection selection = new Selection();
        selection.setContent(selDto.getContent());
        selection.setCorrect(selDto.isCorrect());
        selection.setProblem(problem);

        return selection;
    }
}