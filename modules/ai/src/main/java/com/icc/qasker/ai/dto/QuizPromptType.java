package com.icc.qasker.ai.dto;

import com.icc.qasker.ai.prompt.blank.BlankFormat;
import com.icc.qasker.ai.prompt.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.mutiple.MultipleFormat;
import com.icc.qasker.ai.prompt.mutiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.ox.OXFormat;
import com.icc.qasker.ai.prompt.ox.OXGuideLine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuizPromptType {
    MULTIPLE(MultipleGuideLine.content, MultipleFormat.content),
    BLANK(BlankGuideLine.content, BlankFormat.content),
    OX(OXGuideLine.content, OXFormat.content);

    private final String guideLine;
    private final String format;
}
