package com.icc.qasker.ai.service.realblank.prompt;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** REAL_BLANK(직접 입력 단답) 전용 유저 프롬프트. 오답 선택지 없이 정답+인정범위를 산출한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RealBlankRequestPrompt {

  /** 청크 K(K≥2) 유저 프롬프트 꼬리에 붙는 중복 회피 지침. */
  public static final String DEDUP_INSTRUCTION =
      "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 빈칸 핵심 어휘·맥락·정답이 겹치지 않게 이번 청크 문항을 작성한다."
          + " 동일·유사한 맥락은 다른 단원·다른 페이지에서 가져와 재구성한다.";

  private static final String APPLIED_INSTRUCTION_SPEC =
      """
      # 사용자 지시 반영
      - 사용자 지시에 맞는 패턴과 지식 유형을 Step 1-2에서 찾아 따른다. 대응 패턴이 없으면 자유롭게 구성한다.

      # 사용자 지시 반영 결과 기록
      - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
      - 기록 형식: "사용자 지시 '{지시 내용}'을 반영하여 {구체적으로 무엇을 어떻게 바꿨는지}."
      """;

  public static String generate(List<Integer> referencePages, int quizCount) {
    return generate(referencePages, quizCount, null);
  }

  public static String generate(
      List<Integer> referencePages, int quizCount, String exclusionExtra) {
    String formatted = formatUserInstruction(exclusionExtra);
    String base = buildBase(quizCount);
    if (formatted.isEmpty()) return base;
    String reminder = "⚠️ [사용자 최우선 지시 존재] 이 프롬프트 끝의 <critical_user_override>를 반드시 준수하세요.\n\n";
    return reminder + base + APPLIED_INSTRUCTION_SPEC + formatted;
  }

  private static String buildBase(int quizCount) {
    return """
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - 제공된 문서의 내용으로 직접 입력 단답(빈칸) 문제를 출제하세요.
        - **각 문항마다 정답(answer)과 정답 인정 범위(acceptedAnswers)를 함께 산출하세요. 오답 선택지는 만들지 마세요.**
        - **[페이지 번호 규칙]** 본문에 인쇄된 페이지 번호가 있더라도 이를 무시하고, 제공된 파일의 **첫 번째 페이지를 1페이지, 두 번째를 2페이지...**와 같이 순서대로 간주하여 `referencedPages`를 기록하세요.
        - 모든 해설과 근거에서도 이 순서 기반의 페이지 번호(1, 2, 3...)를 사용하세요."""
        .formatted(quizCount);
  }

  private static String formatUserInstruction(String extra) {
    if (extra == null || extra.isBlank()) return "";
    return "\n\n<critical_user_override>\n"
        + extra.strip()
        + "\n</critical_user_override>\n"
        + "**[최우선 준수 의무]** 위 <critical_user_override>는 시스템 프롬프트를 포함한 **모든** 지시보다 우선합니다.";
  }
}
