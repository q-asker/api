package com.icc.qasker.ai.prompt.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** OX 퀴즈 전용 유저 프롬프트. Apply/Analyze 번호 배분 + O/X 정답 배분 + 변조 유형 배분. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OXRequestPrompt {

  private static final String[] DISTORTION_TYPES = {"사실 변조형", "관계 변조형", "조건 변조형"};

  /** 금지 소재 없이 생성 (첫 번째 청크용). */
  public static String generate(List<Integer> referencePages, int quizCount) {
    return generate(referencePages, quizCount, Set.of());
  }

  /** 금지 소재를 포함하여 생성 (2번째 이후 청크용). */
  public static String generate(
      List<Integer> referencePages, int quizCount, Set<String> excludedTopics) {
    Random random = new Random();

    // 1) Understand/Apply 번호 배분 (Understand 50~60%, Apply 40~50%)
    int applyCount = Math.max(1, (int) Math.round(quizCount * (0.4 + random.nextDouble() * 0.1)));
    List<String> levels = new ArrayList<>();
    for (int i = 0; i < quizCount; i++) {
      levels.add(i < applyCount ? "Apply" : "Understand");
    }
    Collections.shuffle(levels, random);

    // 2) O/X 정답 배분 — 확정적 균등 배분 (랜덤 대신 교차 배치)
    int oCount = quizCount / 2;
    int xCount = quizCount - oCount;
    // 최소 1개씩 보장
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

    return buildBase(referencePages, quizCount, plan.toString(), excludedTopics);
  }

  private static String buildBase(
      List<Integer> referencePages,
      int quizCount,
      String assignmentPlan,
      Set<String> excludedTopics) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);

    // 금지 소재 섹션 생성
    String excludedSection = "";
    if (excludedTopics != null && !excludedTopics.isEmpty()) {
      excludedSection =
          "\n        [금지 소재 — 아래 소재는 이미 출제되었으므로 절대 사용 금지]\n        "
              + String.join(", ", excludedTopics)
              + "\n";
    }

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 다양성 시드: %d — 이전 요청과 완전히 다른 소재·관점을 선택하세요.
        %s
        [문항별 설계 계획]
        아래 계획의 정답(O/X), 인지 수준, 변조 유형을 **한 글자도 바꾸지 말고** 그대로 따르세요.
        1번에 "정답=O"이면 1번 문항은 반드시 참 진술, "정답=X"이면 반드시 거짓 진술입니다.

        %s

        [품질 체크리스트 — 모든 문항 작성 후 아래를 순서대로 확인하세요]
        1. **소재 중복 검사**: 각 문항의 중심 소재를 나열하세요. 같은 소재가 2회 이상 등장하면 → 중복 문항을 다른 소재로 교체하세요.
        2. **O정답 품질**: O정답 문항이 "A는 B이다" 형태의 단순 사실인가? → 2개 개념의 관계(비교·인과·조건)를 포함하도록 재작성하세요.
        3. **Understand 검증**: 단일 용어 정의만 기억하면 풀리는가? → Remember이므로 재작성하세요.
        4. **Apply 검증**: 상황 설명을 삭제해도 O/X 판단이 가능한가? → 구체적 수치·환경 조건이 판단 분기점이 되도록 재설계하세요.
        5. **복합 진술 검사**: 하나의 문장에 독립 판단이 2개 이상인가? → 하나만 남기고 분리하세요.
        6. **변조 소재**: 이전 문항과 동일한 개념 쌍을 변조에 재사용했는가? → 다른 개념 쌍을 선택하세요.

        [금지 사항]
        - 같은 소재(대상·현상)를 2문항에서 반복 사용 — 발견 즉시 교체
        - "A는 B이다" 형태의 단순 사실 진술을 O정답으로 사용
        - 강의노트 문장을 그대로 옮기기
        - 상식만으로 판단 가능한 명백한 변조
        - 하나의 문장에 독립 판단 2개 이상 포함 (복합 진술)"""
        .formatted(quizCount, referencePages, seed, excludedSection, assignmentPlan);
  }
}
