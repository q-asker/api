package com.icc.qasker.ai.service.multiple.prompt;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** MULTIPLE 퀴즈 전용 유저 프롬프트. 1청크 스트리밍 방식. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MultipleRequestPrompt {

  private static final String[] DIVERSITY_INSTRUCTIONS = {
    "Evaluate(A/B) 65~75%%, Apply(C) 25~35%%로 배분하세요. Apply(C) 최소 3문항. stem 구조①~⑤를 골고루 사용하세요. '동시에 고려할 때' 최대 2문항.",
    "Apply(C) 문항을 먼저 3~4개 설계한 뒤, 나머지를 Evaluate(A/B)로 채우세요. stem 구조를 다양화하세요. '동시에 고려할 때' 최대 2문항.",
    "패턴 A(구조①~⑤ 골고루), B(표+서술문+전제 혼합), C(도메인 전이)를 혼합하세요. Apply(C) 최소 25%%. '동시에 고려할 때' 최대 2문항.",
    "Apply(C) 최소 3문항. B 패턴은 표 오류, 서술문 모순, 전제 오류를 다양하게 사용하세요. stem 구조①~⑤ 골고루 배분.",
    "Evaluate와 Apply를 번갈아 배치하세요. stem 구조를 5종 이상 사용하세요. '동시에 고려할 때' 최대 2문항."
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
        - %s 페이지의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 문항 패턴(A=Critiquing, B-1=표Checking, B-3=코드Checking, C=Apply): %s
        - 다양성 시드: %d (이전과 다른 주제·관점·서식을 사용하세요)
        - **주제 중복 금지**: 각 문항은 서로 다른 소재·맥락을 다뤄야 합니다. 같은 대상을 \
2문항에서 반복하지 마세요.
        - **교과서적 정답 금지**: 상식 1줄로 답이 나오는 문항은 출제하지 마세요. 정답 도출에 2개 \
이상의 조건을 교차 고려해야 하도록 설계하세요.
        %s"""
        .formatted(
            quizCount,
            compactPageRange(referencePages),
            diversityInst,
            seed,
            formatUserInstruction(extra));
  }

  /** 페이지 번호 목록을 연속 범위로 압축한다. 예: [1,2,3,5,8,9,10] → "1~3, 5, 8~10" */
  private static String compactPageRange(List<Integer> pages) {
    if (pages == null || pages.isEmpty()) return "";
    if (pages.size() == 1) return String.valueOf(pages.get(0));

    StringBuilder sb = new StringBuilder();
    int start = pages.get(0);
    int prev = start;

    for (int i = 1; i < pages.size(); i++) {
      int curr = pages.get(i);
      if (curr == prev + 1) {
        prev = curr;
      } else {
        appendRange(sb, start, prev);
        sb.append(", ");
        start = curr;
        prev = curr;
      }
    }
    appendRange(sb, start, prev);
    return sb.toString();
  }

  private static void appendRange(StringBuilder sb, int start, int end) {
    if (start == end) {
      sb.append(start);
    } else {
      sb.append(start).append("~").append(end);
    }
  }

  /**
   * 사용자 맞춤 지침을 XML 태그로 감싸 우선순위를 명시한다. null 또는 공백이면 빈 문자열을 반환한다.
   *
   * <p>XML 태그는 LLM이 사용자 지침과 시스템 지시사항을 명확히 구분하도록 돕고, 유저 프롬프트 끝에 배치하여 recency bias를 활용한다.
   */
  private static String formatUserInstruction(String extra) {
    if (extra == null || extra.isBlank()) return "";
    return "\n<user_instruction>\n"
        + extra.strip()
        + "\n</user_instruction>\n위 <user_instruction>은 위의 모든 생성 지시보다 우선합니다. 반드시 준수하세요.";
  }
}
