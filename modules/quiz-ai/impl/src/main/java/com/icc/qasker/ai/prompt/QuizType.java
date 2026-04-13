package com.icc.qasker.ai.prompt;

import com.icc.qasker.ai.prompt.system.BlankGuideLine;
import com.icc.qasker.ai.prompt.system.MultipleGuideLine;
import com.icc.qasker.ai.prompt.system.OXGuideLine;
import com.icc.qasker.ai.prompt.system.i18n.ENGLISH;
import com.icc.qasker.ai.prompt.user.RequestWithPageRefAndCountPrompt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
  MULTIPLE(MultipleGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return RequestWithPageRefAndCountPrompt.generate(referencePages, quizCount);
    }
  },
  BLANK(BlankGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return RequestWithPageRefAndCountPrompt.generate(referencePages, quizCount);
    }
  },
  OX(OXGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return RequestWithPageRefAndCountPrompt.generateForOX(referencePages, quizCount);
    }
  };

  private final String systemGuideLine;

  @Override
  public String getSystemGuideLine(String language) {
    return switch (language) {
      case "EN" -> systemGuideLine + ENGLISH.content;
      case "KO" -> systemGuideLine;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
