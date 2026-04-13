package com.icc.qasker.ai.prompt.system;

import com.icc.qasker.ai.prompt.system.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.system.blank.BlankGuideLineEn;
import com.icc.qasker.ai.prompt.system.multiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.system.multiple.MultipleGuideLineEn;
import com.icc.qasker.ai.prompt.system.ox.OXGuideLine;
import com.icc.qasker.ai.prompt.system.ox.OXGuideLineEn;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
  MULTIPLE(MultipleGuideLine.content, MultipleGuideLineEn.content),
  BLANK(BlankGuideLine.content, BlankGuideLineEn.content),
  OX(OXGuideLine.content, OXGuideLineEn.content);

  private final String guideLine;
  private final String guideLineEn;

  @Override
  public String getGuideLine(String language) {
    return switch (language) {
      case "EN" -> guideLineEn;
      case "KR" -> guideLine;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
