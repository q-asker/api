package com.icc.qasker.ai.prompt.strategy;

import com.icc.qasker.ai.i18n.ENGLISH;
import com.icc.qasker.ai.service.blank.prompt.BlankGuideLine;
import com.icc.qasker.ai.service.blank.prompt.BlankRequestPrompt;
import com.icc.qasker.ai.service.multiple.prompt.MultipleGuideLine;
import com.icc.qasker.ai.service.multiple.prompt.MultipleRequestPrompt;
import com.icc.qasker.ai.service.ox.prompt.OXGuideLine;
import com.icc.qasker.ai.service.ox.prompt.OXRequestPrompt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
  MULTIPLE(MultipleGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return MultipleRequestPrompt.generate(referencePages, quizCount);
    }

    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      return MultipleRequestPrompt.generateWithPlan(referencePages, quizCount, planExtra);
    }
  },
  BLANK(BlankGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return BlankRequestPrompt.generate(referencePages, quizCount);
    }

    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      return BlankRequestPrompt.generate(referencePages, quizCount, planExtra);
    }
  },
  OX(OXGuideLine.content) {
    @Override
    public String generateRequestPrompt(List<Integer> referencePages, int quizCount) {
      return OXRequestPrompt.generate(referencePages, quizCount);
    }

    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      // planExtra에 금지 소재 텍스트가 포함된 경우 유저 프롬프트 끝에 추가
      String base = OXRequestPrompt.generate(referencePages, quizCount);
      return planExtra != null ? base + "\n" + planExtra : base;
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
