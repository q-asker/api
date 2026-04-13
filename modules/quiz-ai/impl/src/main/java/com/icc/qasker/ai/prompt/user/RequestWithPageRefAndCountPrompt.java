package com.icc.qasker.ai.prompt.user;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestWithPageRefAndCountPrompt {

  private static final int DIFFICULTY_THRESHOLD = 3;

  public static String generate(List<Integer> referencePages, int quizCount, String language) {
    if ("EN".equals(language)) {
      String diff =
          quizCount <= DIFFICULTY_THRESHOLD
              ? "- Difficulty: primarily Hard"
              : "- Difficulty: distribute Medium/Hard evenly";
      return """
          [Generation instructions]
          - Generate exactly %d questions.
          - Use pages %s as hints, but freely choose the most suitable pages.
          - Record actual referenced page numbers in each question's referencedPages.
          %s"""
          .formatted(quizCount, referencePages, diff);
    }
    String diff = quizCount <= DIFFICULTY_THRESHOLD ? "- 난이도: 상 위주로 출제하세요" : "- 난이도: 중/상을 균등 배분하세요";
    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지를 힌트로 활용하되, 출제에 적합한 페이지를 자유롭게 선택하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        %s"""
        .formatted(quizCount, referencePages, diff);
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

    if ("EN".equals(language)) {
      String diff =
          quizCount <= DIFFICULTY_THRESHOLD
              ? "- Difficulty: primarily Medium"
              : "- Difficulty: distribute Low/Medium evenly";
      return """
          [Generation instructions]
          - Generate exactly %d questions.
          - Use pages %s as hints, but freely choose the most suitable pages.
          - Record actual referenced page numbers in each question's referencedPages.
          - Answer distribution: %s
          %s"""
          .formatted(quizCount, referencePages, distribution, diff);
    }
    String diff = quizCount <= DIFFICULTY_THRESHOLD ? "- 난이도: 중 위주로 출제하세요" : "- 난이도: 하/중을 균등 배분하세요";
    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지를 힌트로 활용하되, 출제에 적합한 페이지를 자유롭게 선택하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 정답 배분: %s
        %s"""
        .formatted(quizCount, referencePages, distribution, diff);
  }
}
