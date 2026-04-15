package com.icc.qasker.ai.prompt.user;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestWithPageRefAndCountPrompt {

  public static String generate(List<Integer> referencePages, int quizCount) {
    return buildBase(referencePages, quizCount, "");
  }

  /** 문항 계획 결과를 포함한 유저 프롬프트를 생성한다. planExtra가 null이면 기본 프롬프트와 동일. */
  public static String generateWithPlan(
      List<Integer> referencePages, int quizCount, String planExtra) {
    return buildBase(referencePages, quizCount, planExtra != null ? planExtra : "");
  }

  /** OX 타입 전용: 각 문항의 O/X 정답을 사전 배분하여 유저 프롬프트에 포함한다. O 편향을 방지한다 */
  public static String generateForOX(List<Integer> referencePages, int quizCount) {
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

    return buildBase(referencePages, quizCount, "\n- 정답 배분: " + distribution);
  }

  private static String buildBase(List<Integer> referencePages, int quizCount, String extra) {
    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        %s"""
        .formatted(quizCount, referencePages, extra);
  }
}
