package com.icc.qasker.ai.prompt.quiz.common;

import com.icc.qasker.ai.prompt.quiz.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.quiz.multiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.quiz.ox.OXGuideLine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
    MULTIPLE(MultipleGuideLine.content),
    BLANK(BlankGuideLine.content),
    OX(OXGuideLine.content);

    private final String guideLine;
}
