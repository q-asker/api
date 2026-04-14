package com.icc.qasker.ai.prompt;

import java.util.List;

public interface QuizPromptStrategy {

  String getSystemGuideLine(String language);

  /** 청크별 유저 프롬프트를 생성한다. 퀴즈 타입에 따라 난이도·정답 배분 등이 달라진다. */
  String generateRequestPrompt(List<Integer> referencePages, int quizCount);

  /** 문항 계획 결과를 포함한 유저 프롬프트를 생성한다. 기본 구현은 planExtra를 무시한다. */
  default String generateRequestPrompt(
      List<Integer> referencePages, int quizCount, String planExtra) {
    return generateRequestPrompt(referencePages, quizCount);
  }
}
