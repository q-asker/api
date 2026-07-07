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

  /** 문제 생성(Step 1~2)과 해설 생성(Step 3~)의 경계 마커. 청크형 2단계 프롬프트 분리에 사용. */
  private static final String EXPLANATION_MARKER = "# Step 3 — 해설을 작성한다";

  /** Phase 2(해설) 전용 시스템 프롬프트의 자립형 머리말 — 이미 확정된 문항의 해설만 작성하도록 역할을 재정의한다. */
  private static final String EXPLANATION_HEADER =
      """
      # 역할
      당신은 이미 출제·확정된 객관식 문항에 대한 해설을 작성하는 전문가다.
      직전 대화에 제시된 각 문항의 문항·선지·정답은 이미 확정되었으므로 변경하지 말고, 아래 지침에 따라 각 선지별 해설만 생성한다.
      selectionExplanations 배열은 각 문항에 제시된 선지 순서와 동일한 인덱스로 작성한다.

      """;

  @Override
  public String getSystemGuideLine(String language) {
    return withLanguage(systemGuideLine, language);
  }

  /** Phase 1(문제 생성) 시스템 프롬프트 — 해설 작성(Step 3~) 지시를 제외한다. */
  public String getProblemGuideLine(String language) {
    String problemPart = systemGuideLine.split(EXPLANATION_MARKER, 2)[0];
    return withLanguage(problemPart, language);
  }

  /** Phase 2(해설 생성) 시스템 프롬프트 — 해설 작성 지시(Step 3~)만 자립형으로 재구성한다. */
  public String getExplanationGuideLine(String language) {
    String[] parts = systemGuideLine.split(EXPLANATION_MARKER, 2);
    String explanationPart =
        parts.length > 1 ? EXPLANATION_HEADER + EXPLANATION_MARKER + parts[1] : systemGuideLine;
    return withLanguage(explanationPart, language);
  }

  private String withLanguage(String base, String language) {
    return switch (language) {
      case "EN" -> base + ENGLISH.content;
      case "KO" -> base;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
