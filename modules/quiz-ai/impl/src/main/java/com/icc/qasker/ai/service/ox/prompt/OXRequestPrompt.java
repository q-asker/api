package com.icc.qasker.ai.service.ox.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** OX 퀴즈 전용 유저 프롬프트. Understand/Apply 번호 배분 + O/X 정답 배분 + 변조 유형 배분. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OXRequestPrompt {

  private static final String[] DISTORTION_TYPES = {"사실 변조형", "관계 변조형", "조건 변조형"};

  /** customInstruction이 있으면 XML 태그로 감싸 유저 프롬프트 끝에 우선 삽입한다. */
  public static String generateWithUserInstruction(
      List<Integer> referencePages, int quizCount, String customInstruction) {
    String base = generate(referencePages, quizCount);
    return base + formatUserInstruction(customInstruction);
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

  public static String generate(List<Integer> referencePages, int quizCount) {
    Random random = new Random();

    // 1) Understand/Apply 번호 배분 (Understand 50~60%, Apply 40~50%)
    int applyCount = Math.max(1, (int) Math.round(quizCount * (0.4 + random.nextDouble() * 0.1)));
    List<String> levels = new ArrayList<>();
    for (int i = 0; i < quizCount; i++) {
      levels.add(i < applyCount ? "Apply" : "Understand");
    }
    Collections.shuffle(levels, random);

    // 2) O/X 정답 배분 — 확정적 균등 배분
    int oCount = quizCount / 2;
    int xCount = quizCount - oCount;
    if (oCount == 0) {
      oCount = 1;
      xCount = quizCount - 1;
    }
    if (xCount == 0) {
      xCount = 1;
      oCount = quizCount - 1;
    }
    List<String> answers = new ArrayList<>();
    for (int i = 0; i < oCount; i++) answers.add("O");
    for (int i = 0; i < xCount; i++) answers.add("X");
    Collections.shuffle(answers, random);

    // 3) X 문항에 변조 유형 배분 (골고루)
    int distortionIdx = random.nextInt(DISTORTION_TYPES.length);

    StringBuilder plan = new StringBuilder();
    for (int i = 0; i < quizCount; i++) {
      plan.append(i + 1).append("번: ").append(levels.get(i)).append(", 정답=").append(answers.get(i));
      if ("X".equals(answers.get(i))) {
        plan.append(", 변조=").append(DISTORTION_TYPES[distortionIdx % DISTORTION_TYPES.length]);
        distortionIdx++;
      }
      plan.append("\n");
    }

    return buildBase(referencePages, quizCount, plan.toString());
  }

  private static String buildBase(
      List<Integer> referencePages, int quizCount, String assignmentPlan) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지의 내용으로 문제를 출제하세요.
        - 다양성 시드: %d
        - **주제 중복 금지**: 각 문항은 서로 다른 소재·맥락을 다뤄야 합니다.

        [문항별 설계 계획 — 한 글자도 바꾸지 말고 따르세요]
        %s

        [품질 체크 — 작성 후 반드시 확인]
        1. **O정답 품질**: 볼드 개념 2개 미만이면 재작성. "A는 B이다" 형태면 재작성.
        2. **Remember 탈락**: 개념 1개만으로 풀리면 재작성.
        3. **복합 진술**: 독립 판단 2개↑이면 분리.

        [금지]
        - 같은 소재 2문항 반복 / "A는 B이다" O정답 / 강의노트 그대로 인용
        - 상식으로 판단 가능한 변조 / "모든·항상·절대·반드시·필수" 변조
        - 독립 판단 2개↑ 복합 진술"""
        .formatted(quizCount, compactPageRange(referencePages), seed, assignmentPlan);
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
}
