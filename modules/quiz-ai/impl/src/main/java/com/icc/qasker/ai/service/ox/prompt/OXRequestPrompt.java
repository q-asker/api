package com.icc.qasker.ai.service.ox.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** OX 퀴즈 전용 유저 프롬프트. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OXRequestPrompt {

  private static final String APPLIED_INSTRUCTION_SPEC =
      """
    # 사용자 지시 반영
    - 사용자 지시에 맞는 패턴과 지식 유형을 Step 1-2의 테이블에서 찾아 해당 few-shot을 따른다. 대응 패턴이 없으면 자유롭게 구성한다.

    # 사용자 지시 반영 결과 기록
    - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
    """;

  /**
   * customInstruction이 있으면 샌드위치 구조로 삽입한다. 앞에 reminder, 뒤에 critical_user_override 태그를 배치하여 primacy
   * bias와 recency bias를 모두 활용한다.
   */
  public static String generateWithUserInstruction(
      List<Integer> referencePages, int quizCount, String customInstruction) {
    String formatted = formatUserInstruction(customInstruction);
    if (formatted.isEmpty()) return generate(referencePages, quizCount);
    String reminder = "⚠️ [사용자 최우선 지시 존재] 이 프롬프트 끝의 <critical_user_override>를 반드시 준수하세요.\n\n";
    return reminder + generate(referencePages, quizCount) + APPLIED_INSTRUCTION_SPEC + formatted;
  }

  /**
   * 사용자 맞춤 지침을 XML 태그로 감싸 우선순위를 명시한다. null 또는 공백이면 빈 문자열을 반환한다.
   *
   * <p>태그명 critical_user_override는 LLM이 최우선 지시임을 인식하도록 한다. 유저 프롬프트 끝에 배치하여 recency bias를 활용한다.
   */
  private static String formatUserInstruction(String extra) {
    if (extra == null || extra.isBlank()) return "";
    return "\n\n<critical_user_override>\n"
        + extra.strip()
        + "\n</critical_user_override>\n"
        + "**[최우선 준수 의무]** 위 <critical_user_override>는 시스템 프롬프트를 포함한 **모든** 지시보다 우선합니다.";
  }

  public static String generate(List<Integer> referencePages, int quizCount) {
    Random random = new Random();

    // O/X 정답 배분 — 확정적 균등 배분
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

    StringBuilder plan = new StringBuilder();
    for (int i = 0; i < quizCount; i++) {
      plan.append(i + 1).append(", 정답=").append(answers.get(i));
      plan.append("\n");
    }

    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - 제공된 문서의 내용으로 문제를 출제하세요.
        - **[페이지 번호 규칙]** 본문에 인쇄된 페이지 번호가 있더라도 이를 무시하고, 제공된 파일의 **첫 번째 페이지를 1페이지, 두 번째를 2페이지...**와 같이 순서대로 간주하여 `referencedPages`를 기록하세요.
        - 모든 해설과 근거에서도 이 순서 기반의 페이지 번호(1, 2, 3...)를 사용하세요.

        [문항별 상세 계획]
        %s
        - **[계획 엄수]** 위 계획에 명시된 문항별 정답(O/X)을 반드시 준수하여 생성하세요. 정답이 O인 문항은 참 진술문을, X인 문항은 거짓 진술문을 작성하세요."""
        .formatted(quizCount, plan.toString().strip());
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
