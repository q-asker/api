package com.icc.qasker.ai.prompt.system;

import com.icc.qasker.ai.prompt.system.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.system.multiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.system.ox.OXGuideLine;
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
