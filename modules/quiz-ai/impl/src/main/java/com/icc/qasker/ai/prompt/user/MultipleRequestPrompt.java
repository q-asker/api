package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** MULTIPLE 퀴즈 전용 유저 프롬프트. Evaluate/Apply 2수준 혼합. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MultipleRequestPrompt {

  private static final String[] DIVERSITY_INSTRUCTIONS = {
    "Evaluate(A/B) 65~75%%, Apply(C) 25~35%%로 배분하세요. Apply(C) 최소 3문항 포함 필수.",
    "Apply(C) 문항을 먼저 3~4개 설계한 뒤, 나머지를 Evaluate(A/B)로 채우세요.",
    "패턴 A(트레이드오프), B(오류탐지+판단), C(도메인 전이)를 골고루 혼합하세요. Apply(C) 최소 25%% 포함.",
    "Apply(C) 최소 3문항을 반드시 포함하세요. B 패턴은 표 오류, 서술문 모순, 전제 오류 등을 다양하게 사용하세요.",
    "Evaluate와 Apply를 번갈아 배치하세요. Apply는 강의노트와 전혀 다른 분야에 원리를 전이하는 시나리오로 설계하세요."
  };

  public static String generate(List<Integer> referencePages, int quizCount) {
    return buildBase(referencePages, quizCount, "");
  }

  /** 문항 계획 결과를 포함한 유저 프롬프트를 생성한다. planExtra가 null이면 기본 프롬프트와 동일. */
  public static String generateWithPlan(
      List<Integer> referencePages, int quizCount, String planExtra) {
    return buildBase(referencePages, quizCount, planExtra != null ? planExtra : "");
  }

  private static String buildBase(List<Integer> referencePages, int quizCount, String extra) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);
    String diversityInst = DIVERSITY_INSTRUCTIONS[rng.nextInt(DIVERSITY_INSTRUCTIONS.length)];

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 문항 패턴(A=Critiquing, B-1=표Checking, B-2=서술문/전제Checking, B-3=코드Checking, C=Apply): %s
        - 다양성 시드: %d (이전과 다른 주제·관점·서식을 사용하세요)
        - **주제 중복 금지**: 각 문항은 서로 다른 소재·맥락을 다뤄야 합니다. 같은 대상(예: 가옥, \
의복, 수자원)을 2문항에서 반복하지 마세요.
        - **교과서적 정답 금지**: 상식 1줄로 답이 나오는 문항은 출제하지 마세요. 정답 도출에 2개 \
이상의 조건을 교차 고려해야 하도록 설계하세요.
        %s"""
        .formatted(quizCount, referencePages, diversityInst, seed, extra);
  }
}
