package com.icc.qasker.ai.strategy;

import com.icc.qasker.ai.i18n.ENGLISH;
import com.icc.qasker.ai.service.blank.prompt.BlankGuideLine;
import com.icc.qasker.ai.service.blank.prompt.BlankRequestPrompt;
import com.icc.qasker.ai.service.essay.prompt.EssayGuideLine;
import com.icc.qasker.ai.service.essay.prompt.EssayRequestPrompt;
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
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      return MultipleRequestPrompt.generateWithPlan(referencePages, quizCount, planExtra);
    }
  },
  BLANK(BlankGuideLine.content) {
    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      return BlankRequestPrompt.generate(referencePages, quizCount, planExtra);
    }
  },
  OX(OXGuideLine.content) {
    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      // customInstruction이 있으면 XML 태그로 감싸 유저 프롬프트 끝에 우선 삽입
      return OXRequestPrompt.generateWithUserInstruction(referencePages, quizCount, planExtra);
    }
  },
  ESSAY(EssayGuideLine.content) {
    @Override
    public String generateRequestPrompt(
        List<Integer> referencePages, int quizCount, String planExtra) {
      return EssayRequestPrompt.generate(referencePages, quizCount, planExtra);
    }
  };

  private final String systemGuideLine;

  /** 시스템 지침 내 문제 생성(Step 1~2)과 해설 작성(Step 3~) 섹션의 경계 마커. getProblemGuideLine이 해설 섹션을 잘라낼 때 쓴다. */
  private static final String EXPLANATION_MARKER = "# Step 3 — 해설을 작성한다";

  @Override
  public String getSystemGuideLine(String language) {
    return withLanguage(systemGuideLine, language);
  }

  /** 해설 작성(Step 3~) 지시를 제외한 문제 생성 지침. 품질 검증 프롬프트가 문제 부분만 참고할 때 쓴다. */
  public String getProblemGuideLine(String language) {
    String problemPart = systemGuideLine.split(EXPLANATION_MARKER, 2)[0];
    return withLanguage(problemPart, language);
  }

  private String withLanguage(String base, String language) {
    return switch (language) {
      case "EN" -> base + ENGLISH.content;
      case "KO" -> base;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
