package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectionMapper {

    public Selection fromResponse(SelectionsOfAI dto, Problem problem) {
        return Selection.builder()
            .content(dto.getContent())
            .correct(dto.isCorrect())
            .problem(problem)
            .build();
    }

}
