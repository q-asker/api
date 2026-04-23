package com.icc.qasker.ai.service.multiple.prompt;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** MULTIPLE 퀴즈 전용 유저 프롬프트. 1청크 스트리밍 방식. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MultipleRequestPrompt {

  public static String generate(List<Integer> referencePages, int quizCount) {
    return buildBase(referencePages, quizCount);
  }

  private static String buildBase(List<Integer> referencePages, int quizCount) {
    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - 제공된 문서의 내용으로 문제를 출제하세요.
        - **[페이지 번호 규칙]** 본문에 인쇄된 페이지 번호가 있더라도 이를 무시하고, 제공된 파일의 **첫 번째 페이지를 1페이지, 두 번째를 2페이지...**와 같이 순서대로 간주하여 `referencedPages`를 기록하세요.
        - 모든 해설과 근거에서도 이 순서 기반의 페이지 번호(1, 2, 3...)를 사용하세요."""
        .formatted(quizCount);
  }

  private static final String APPLIED_INSTRUCTION_SPEC =
      """
      # 사용자 지시 반영
      - 지시가 특정 형식을 요청하면, 그 형식에 대응하는 전략이 존재하면 해당 전략을 우선 선택한다.
      - 대응 전략이 없는 형식은 요청 형식에 맞게 질문문을 자유롭게 구성다.

      # 사용자 지시 반영 결과 기록
      - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
      - 기록 형식: "사용자 지시 '{지시 내용}'을 반영하여 {구체적으로 무엇을 어떻게 바꿨는지}."
      """;

  /**
   * planExtra가 있으면 샌드위치 구조로 삽입한다. 앞에 reminder, 뒤에 critical_user_override 태그를 배치하여 primacy bias와
   * recency bias를 모두 활용한다.
   */
  public static String generateWithPlan(
      List<Integer> referencePages, int quizCount, String planExtra) {
    String formatted = formatUserInstruction(planExtra);
    String base = buildBase(referencePages, quizCount);
    if (formatted.isEmpty()) return base;
    return base + APPLIED_INSTRUCTION_SPEC + formatted;
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
}
