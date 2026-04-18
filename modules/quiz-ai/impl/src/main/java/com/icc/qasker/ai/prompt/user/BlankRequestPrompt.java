package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** BLANK 퀴즈 전용 유저 프롬프트. Remember/Understand 2수준. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlankRequestPrompt {

  /** 용어 선택 전략을 구조적으로 분화하여 세트 간 정답 중복을 최소화한다. 각 전략은 강의노트에서 서로 다른 유형의 용어를 우선 선택하도록 유도한다. */
  private static final String[] DIVERSITY_INSTRUCTIONS = {
    // 전략 A: 정의·명칭 중심 — 강의노트에서 "~란", "~이다"로 정의된 용어 우선
    "**용어 선택 전략: 정의·명칭 중심**. 강의노트에서 '~란', '~이다', '~를 말한다'로 정의된"
        + " 용어를 우선 선택하세요. **Understand 문항 최소 3개 필수**: 전체 문항 중 최소 3문항은"
        + " 반드시 Understand(이해) 수준으로 출제하세요. 3개 미만이면 Remember 문항을 Understand로"
        + " 전환하세요.",
    // 전략 B: 분류·유형 중심 — 상위/하위 범주, 유형 구분에 사용된 용어 우선
    "**용어 선택 전략: 분류·유형 중심**. 강의노트에서 분류 체계(상위/하위 범주), 유형 구분,"
        + " '~종류', '~유형'에 해당하는 용어를 우선 선택하세요. **Understand 문항 최소 3개 필수**:"
        + " 전체 문항 중 최소 3문항은 반드시 Understand(이해) 수준으로 출제하세요. 3개 미만이면"
        + " Remember 문항을 Understand로 전환하세요.",
    // 전략 C: 원인·결과 중심 — 인과관계, 메커니즘, 과정에 등장하는 용어 우선
    "**용어 선택 전략: 원인·결과 중심**. 강의노트에서 인과관계('~때문에', '~의 결과')나 과정·"
        + "메커니즘에 등장하는 용어를 우선 선택하세요. **Understand 문항 최소 3개 필수**: 전체"
        + " 문항 중 최소 3문항은 반드시 Understand(이해) 수준으로 출제하세요. 3개 미만이면"
        + " Remember 문항을 Understand로 전환하세요.",
    // 전략 D: 비교·대조 중심 — 대비되는 쌍, 차이점에 사용된 용어 우선
    "**용어 선택 전략: 비교·대조 중심**. 강의노트에서 대비되는 개념 쌍('A와 달리 B는', 'A에"
        + " 비해 B는')의 용어를 우선 선택하세요. **Understand 문항 최소 3개 필수**: 전체 문항 중"
        + " 최소 3문항은 반드시 Understand(이해) 수준으로 출제하세요. 3개 미만이면 Remember"
        + " 문항을 Understand로 전환하세요.",
    // 전략 E: 고유명사·사례 중심 — 구체적 지명, 인명, 사례명 우선
    "**용어 선택 전략: 고유명사·사례 중심**. 강의노트에서 구체적 지명, 인명, 사례명, 고유 명칭을"
        + " 우선 선택하세요. **Understand 문항 최소 3개 필수**: 전체 문항 중 최소 3문항은 반드시"
        + " Understand(이해) 수준으로 출제하세요. 3개 미만이면 Remember 문항을 Understand로"
        + " 전환하세요.",
    // 전략 F: 후반부 중심 — 강의노트 후반부 내용 우선
    "**용어 선택 전략: 후반부 우선**. 강의노트의 후반부(뒷부분 절반)에 등장하는 용어를 우선"
        + " 선택하세요. 앞부분에서만 출제하지 마세요. **Understand 문항 최소 3개 필수**: 전체"
        + " 문항 중 최소 3문항은 반드시 Understand(이해) 수준으로 출제하세요. 3개 미만이면"
        + " Remember 문항을 Understand로 전환하세요.",
    // 전략 G: 전반부 중심 — 강의노트 전반부 내용 우선
    "**용어 선택 전략: 전반부 우선**. 강의노트의 전반부(앞부분 절반)에 등장하는 용어를 우선"
        + " 선택하세요. **Understand 문항 최소 3개 필수**: 전체 문항 중 최소 3문항은 반드시"
        + " Understand(이해) 수준으로 출제하세요. 3개 미만이면 Remember 문항을 Understand로"
        + " 전환하세요."
  };

  public static String generate(List<Integer> referencePages, int quizCount) {
    return generate(referencePages, quizCount, null);
  }

  /** 제외 목록(exclusionExtra)을 포함한 유저 프롬프트 생성. Wave 간 중복 방지용. */
  public static String generate(
      List<Integer> referencePages, int quizCount, String exclusionExtra) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);
    String diversityInst = DIVERSITY_INSTRUCTIONS[rng.nextInt(DIVERSITY_INSTRUCTIONS.length)];

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - %s
        - **정답 중복 절대 금지**: 세트 내에서 같은 정답 용어가 2회 이상 등장하면 안 됩니다. \
동일 정답이 발견되면 해당 문항을 삭제하고 다른 용어로 재생성하세요. 문장(content)이 \
거의 동일한 문항 쌍도 한쪽을 삭제하고 다른 소재로 재작성하세요.
        - **정답 명사 원칙**: 빈칸 정답은 반드시 명사(개념명, 용어, 명칭)여야 합니다. \
"헐렁한", "두꺼운", "평평한" 등 형용사를 정답으로 사용하면 안 됩니다. \
정답이 "~한/~운/~은/~된"으로 끝나면 명사가 아니므로 교체하세요.
        - 다양성 시드: %d
        %s"""
        .formatted(
            quizCount,
            referencePages,
            diversityInst,
            seed,
            exclusionExtra != null ? exclusionExtra : "");
  }
}
