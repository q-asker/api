package com.icc.qasker.ai.prompt.system;

import com.icc.qasker.ai.prompt.system.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.system.multiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.system.ox.OXGuideLine;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
  MULTIPLE(MultipleGuideLine.content),
  BLANK(BlankGuideLine.content),
  OX(OXGuideLine.content);

  private final String guideLine;

  private static final String ENGLISH_OUTPUT_SUFFIX =
      "\n\n---\n\n> **OUTPUT LANGUAGE**: You MUST write ALL output in **English**. "
          + "This includes question content, selections, explanations, and quizExplanation. "
          + "Follow the guidelines above exactly, but produce every text field in English.";

  @Override
  public String getGuideLine(String language) {
    return switch (language) {
      case "EN" -> guideLine + ENGLISH_OUTPUT_SUFFIX;
      case "KO" -> guideLine;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
