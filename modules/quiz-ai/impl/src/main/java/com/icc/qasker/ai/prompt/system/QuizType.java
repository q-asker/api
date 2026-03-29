package com.icc.qasker.ai.prompt.system;

import com.icc.qasker.ai.prompt.system.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.system.multiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.system.ox.OXGuideLine;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
  MULTIPLE(MultipleGuideLine.content),
  BLANK(BlankGuideLine.content),
  OX(OXGuideLine.content);

  private static final String LANGUAGE_SUFFIX_EN =
      """

      ---

      ## Output Language
      - You MUST write all output (content, selections, explanations) in **English**.
      - selections[].content must contain ONLY the answer text (one concise sentence). \
      Never put explanations, diagnoses, or reasoning in the content field.
      """;

  private final String guideLine;

  @Override
  public String getGuideLine(String language) {
    return "EN".equals(language) ? guideLine + LANGUAGE_SUFFIX_EN : guideLine;
  }
}
