package com.icc.qasker.ai.prompt.user;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestWithPageRefAndCountPrompt {

  private static final String[] DIVERSITY_INSTRUCTIONS = {
    "패턴 A, B-1, B-2를 골고루 혼합하세요. 동일 패턴이 50%를 넘지 않도록 하세요.",
    "각 문항에 가장 적합한 패턴(A/B-1/B-2)을 선택하되, 최소 2종 이상 사용하세요.",
    "강의노트 내용에 맞는 형식을 자율 선택하되, 3종 패턴을 모두 사용하세요.",
    "표, 다이어그램, 서술문을 골고루 활용하세요. 한 형식에 편중하지 마세요.",
    "문항마다 내용에 최적인 패턴을 선택하세요. 단, 세트 전체에서 다양성을 확보하세요."
  };

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
    // 요청마다 랜덤 시드와 패턴 순서를 생성하여 동일 캐시에서도 다양한 결과를 유도
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);
    String diversityInst = DIVERSITY_INSTRUCTIONS[rng.nextInt(DIVERSITY_INSTRUCTIONS.length)];

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 문항 패턴(A=Critiquing, B-1=표/다이어그램 Checking, B-2=서술문 Checking): %s
        - 다양성 시드: %d (이전과 다른 주제·관점·서식을 사용하세요)
        %s"""
        .formatted(quizCount, referencePages, diversityInst, seed, extra);
  }
}
