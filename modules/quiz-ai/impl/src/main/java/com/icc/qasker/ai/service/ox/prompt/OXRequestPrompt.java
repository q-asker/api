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
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 다양성 시드: %d

        [문항별 설계 계획]
        아래 계획의 정답(O/X), 인지 수준, 변조 유형을 **한 글자도 바꾸지 말고** 그대로 따르세요.
        1번에 "정답=O"이면 1번 문항은 반드시 참 진술, "정답=X"이면 반드시 거짓 진술입니다.

        %s

        [품질 체크리스트 — 모든 문항 작성 후 아래를 순서대로 확인하세요]
        1. **소재 중복 검사**: 25문항 전체에서 동일 소재가 2회 이상 등장하면 → 다른 소재로 교체. 같은 개념의 다른 속성도 같은 소재입니다.
        2. **Remember 탈락 검사 (최우선)**: 각 문항에서 개념을 1개만 남겨 보세요. 그 정의만으로 풀리면 Remember → 재작성. 강의노트 원문과 1:1 대응되면 Remember → 재작성.
        3. **O정답 품질 (가장 흔한 감점)**: O정답 문항을 하나씩 점검하세요. (a) 개념 1개의 정의·속성만 서술하는가? (b) 강의노트 문장을 그대로 옮겼는가? (c) "A는 B이다" 형태인가? → 하나라도 해당하면 2개 개념의 관계(비교·인과·조건-결과)를 포함하도록 재작성하세요.
        4. **Apply 검증**: (a) 상황 설명을 삭제해도 O/X 판단이 가능한가? (b) 강의노트에 나온 동일 예시를 재사용하는가? → 하나라도 해당하면 새로운 상황·조건으로 재설계하세요.
        5. **절대 표현 검사**: "모든", "항상", "절대", "반드시", "유일한", "필수적인", "필수" 등이 포함된 문항이 있는가? → 해당 표현 없이 변조를 재설계하세요.
        6. **복합 진술 검사**: 하나의 문장에 독립 판단이 2개 이상인가? → 하나만 남기고 분리하세요.

        [금지 사항]
        - 같은 소재(대상·현상)를 2문항에서 반복 사용 — 발견 즉시 교체
        - "A는 B이다" 형태의 단순 사실 진술을 O정답으로 사용
        - 강의노트 문장을 그대로 옮기기
        - 상식만으로 판단 가능한 명백한 변조
        - "모든", "항상", "절대", "반드시", "유일한", "필수적인", "필수"를 변조에 사용
        - Apply 문항에서 강의노트에 나온 동일 예시를 그대로 재사용
        - 하나의 문장에 독립 판단 2개 이상 포함 (복합 진술)"""
        .formatted(quizCount, referencePages, seed, assignmentPlan);
  }
}
