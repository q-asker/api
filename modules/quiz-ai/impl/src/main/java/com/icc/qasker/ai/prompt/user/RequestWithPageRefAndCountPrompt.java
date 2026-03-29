package com.icc.qasker.ai.prompt.user;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestWithPageRefAndCountPrompt {

  private static final String LANGUAGE_INSTRUCTION_EN = "\n  - Write all output in English.";

  public static String generate(List<Integer> referencePages, int quizCount, String language) {
    String base =
        """
      [생성 지시]
      - 강의노트의 %s 페이지를 참고하여 정확히 %d개의 문제를 생성하세요."""
            .formatted(referencePages, quizCount);
    return "EN".equals(language) ? base + LANGUAGE_INSTRUCTION_EN : base;
  }

  /** OX 타입 전용: 각 문항의 O/X 정답을 사전 배분하여 유저 프롬프트에 포함한다. O 편향을 방지한다 */
  public static String generateForOX(List<Integer> referencePages, int quizCount, String language) {
    java.util.Random random = new java.util.Random();
    List<String> assignments = new ArrayList<>();
    for (int i = 0; i < quizCount; i++) {
      assignments.add(random.nextBoolean() ? "O" : "X");
    }

    StringBuilder distribution = new StringBuilder();
    for (int i = 0; i < assignments.size(); i++) {
      if (i > 0) distribution.append(", ");
      distribution.append(i + 1).append("번→").append(assignments.get(i));
    }

    String base =
        """
      [생성 지시]
      - 강의노트의 %s 페이지를 참고하여 정확히 %d개의 문제를 생성하세요.
      - 정답 배분: %s"""
            .formatted(referencePages, quizCount, distribution);
    return "EN".equals(language) ? base + LANGUAGE_INSTRUCTION_EN : base;
  }
}
