package com.icc.qasker.ai.prompt.quiz.common;

import com.icc.qasker.ai.prompt.quiz.blank.BlankFormat;
import com.icc.qasker.ai.prompt.quiz.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.quiz.mutiple.MultipleFormat;
import com.icc.qasker.ai.prompt.quiz.mutiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.quiz.ox.OXFormat;
import com.icc.qasker.ai.prompt.quiz.ox.OXGuideLine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
    MULTIPLE(MultipleGuideLine.content, MultipleFormat.content),
    BLANK(BlankGuideLine.content, BlankFormat.content),
    OX(OXGuideLine.content, OXFormat.content);

    private final String guideLine;
    private final String format;
}
