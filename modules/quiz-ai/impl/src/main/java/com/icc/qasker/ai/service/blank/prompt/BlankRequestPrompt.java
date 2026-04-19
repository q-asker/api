package com.icc.qasker.ai.service.blank.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** BLANK 퀴즈 전용 유저 프롬프트. Remember/Understand 2수준. 1청크 단일 호출. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlankRequestPrompt {

  /** MULTIPLE에서 검증된 패턴: 5개 랜덤 변이가 단일 지시보다 다양성 +0.67. 매 생성마다 다른 전략이 선택되어 같은 PDF에서도 다른 세트가 나옴. */
  private static final String[] DIVERSITY_INSTRUCTIONS = {
    "Remember 70~80%%, Understand 20~30%%로 배분하세요. Understand 최소 5문항. "
        + "정의·명칭, 분류·유형, 원인·결과, 비교·대조 유형을 골고루 포함하세요.",
    "Understand 문항을 먼저 5~6개 설계한 뒤, 나머지를 Remember로 채우세요. " + "강의노트 전반부와 후반부에서 균등하게 출제하세요.",
    "빈칸 유형을 다양화하세요: 정의형('~를 ___라고 한다'), 비교형('A와 달리 ___는'), "
        + "원인형('~때문에 ___가 발생한다'). 한 유형에 50%%이상 편중 금지.",
    "Understand 최소 5문항. 서술문에 비교/인과/분류 문맥을 반드시 포함하세요. " + "정의형만 반복하지 마세요. 고유명사·사례명도 빈칸으로 활용하세요.",
    "Remember와 Understand를 번갈아 배치하세요. 같은 하위 주제에서 3문항 이상 출제하지 마세요. " + "강의노트의 핵심 표·다이어그램에서도 출제하세요."
  };

  public static String generate(List<Integer> referencePages, int quizCount) {
    return generate(referencePages, quizCount, null);
  }

  public static String generate(
      List<Integer> referencePages, int quizCount, String exclusionExtra) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int seed = rng.nextInt(10000, 99999);
    String diversityInst = DIVERSITY_INSTRUCTIONS[rng.nextInt(DIVERSITY_INSTRUCTIONS.length)];
    String bloomsAssignment = buildBloomsAssignment(quizCount);

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - %s 페이지들의 내용으로 문제를 출제하세요.
        - 각 문항의 referencedPages에 실제 참조한 페이지 번호를 기록하세요.
        - 문항 배분: %s
        - %s
        - 다양성 시드: %d (이전과 다른 주제·관점을 사용하세요)
        - **주제 중복 금지**: 각 문항은 서로 다른 소재·맥락을 다뤄야 합니다. \
같은 정답 용어가 2회 이상 등장하면 안 됩니다.
        - **정답 명사 원칙**: 빈칸 정답은 반드시 명사(개념명, 용어, 명칭)여야 합니다. \
형용사("헐렁한"), 동사("구분하지"), 조사("통해서")를 정답으로 사용하면 안 됩니다.
        %s"""
        .formatted(
            quizCount,
            compactPageRange(referencePages),
            diversityInst,
            bloomsAssignment,
            seed,
            exclusionExtra != null ? exclusionExtra : "");
  }

  /**
   * Bloom's 수준을 문항 번호별로 명시 할당한다. Remember 70~80%, Understand 20~30%. 매 생성 요청마다 다른 번호가 선택되어 다양성 확보.
   */
  private static String buildBloomsAssignment(int quizCount) {
    int understandCount = Math.max(1, (int) Math.round(quizCount * 0.25));
    List<Integer> indices =
        IntStream.rangeClosed(1, quizCount)
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));
    Collections.shuffle(indices);
    String nums =
        indices.subList(0, Math.min(understandCount, indices.size())).stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
    return "**Bloom's 수준 할당**: 문항 "
        + nums
        + "번은 반드시 **Understand(이해)** 수준으로 출제하세요. "
        + "Understand 문항은 반드시 '~와 달리', '~에 비해', '~때문에' 등 비교/인과/분류 문맥을 포함해야 합니다. "
        + "나머지 문항은 Remember(기억) 수준으로 출제하세요.";
  }

  /** 페이지 번호 목록을 연속 범위로 압축한다. [1,2,3,5,8,9,10] → "1~3, 5, 8~10" */
  static String compactPageRange(List<Integer> pages) {
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
