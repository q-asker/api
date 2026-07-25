package com.icc.qasker.ai.service.blank.prompt;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** BLANK 퀴즈 전용 유저 프롬프트. Remember/Understand 2수준. 1청크 단일 호출. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlankRequestPrompt {

  /**
   * REAL_BLANK 전용 꼬리 지침. 정답 선지의 {@code acceptedAnswers}(빈칸별 인정 답)를 함께 생성하도록 지시한다. 스키마에 필드가 존재할
   * 때만(realBlank) 부착된다.
   */
  public static final String REAL_BLANK_ACCEPTED_ANSWERS_INSTRUCTION =
      """

      > **REAL_BLANK 인정 답 생성 규칙 (정답 선지의 `acceptedAnswers` 필드)**
      > 이 퀴즈는 사용자가 정답을 **직접 타이핑**한다. 표기만 다른 답이 오답 처리되지 않도록, 정답 선지(`correct: true`)에만
      > 빈칸별 인정 답 목록을 `acceptedAnswers`에 채운다. (오답 선지는 빈 배열 `[]`.)
      > - **형태**: 빈칸별 배열의 배열. 외곽 배열의 index는 정답 `content`의 **콤마 구분 빈칸 순서와 1:1**. 단일 빈칸이면 `[[...]]`,
      >   2빈칸이면 `[[...빈칸1...], [...빈칸2...]]`.
      > - **넣을 것**: 완전 동의어, 통용되는 약어·정식명칭 상호(예: `이벤트 루프`↔`event loop`), 한↔영 표기(예: `운동량`↔`momentum`).
      > - **넣지 말 것**: ① 정답 자신(중복), ② 이 문항의 **오답(함정) 선지와 뜻이 겹치는 표현**, ③ 상위/하위/인접 개념,
      >   ④ 오탈자·철자 변형, ⑤ 애매하면 넣지 않는다(보수적). 표기 차이(공백·대소문자·문장부호)는 시스템이 자동 관용하므로 목록에 넣지 않는다.
      > - 확신하는 인정 답이 없으면 해당 빈칸은 빈 배열 `[]`로 둔다.
      """;

  /** 청크 K(K≥2) 유저 프롬프트 꼬리에 붙는 중복 회피 지침. */
  public static final String DEDUP_INSTRUCTION =
      "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 빈칸 핵심 어휘·맥락·정답 분포(answerIndex)가 겹치지 않게 이번 청크 문항을 작성한다."
          + " stemSummary와 동일·유사한 맥락은 다른 단원·다른 페이지에서 가져와 재구성하고,"
          + " 정답 위치(answerIndex)가 직전 청크와 같은 쪽으로 쏠리지 않게 분산한다.";

  private static final String APPLIED_INSTRUCTION_SPEC =
      """
      # 사용자 지시 반영
      - 사용자 지시에 맞는 패턴과 지식 유형을 Step 1-2의 테이블에서 찾아 해당 few-shot을 따른다. 대응 패턴이 없으면 자유롭게 구성한다.

      # 사용자 지시 반영 결과 기록
      - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
      - 기록 형식: "사용자 지시 '{지시 내용}'을 반영하여 {구체적으로 무엇을 어떻게 바꿨는지}."
      """;

  public static String generate(List<Integer> referencePages, int quizCount) {
    return generate(referencePages, quizCount, null);
  }

  /**
   * exclusionExtra가 있으면 샌드위치 구조로 삽입한다. 앞에 reminder, 뒤에 critical_user_override 태그를 배치하여 primacy
   * bias와 recency bias를 모두 활용한다.
   */
  public static String generate(
      List<Integer> referencePages, int quizCount, String exclusionExtra) {
    String formatted = formatUserInstruction(exclusionExtra);
    String base = buildBase(referencePages, quizCount);
    if (formatted.isEmpty()) return base;
    String reminder = "⚠️ [사용자 최우선 지시 존재] 이 프롬프트 끝의 <critical_user_override>를 반드시 준수하세요.\n\n";
    return reminder + base + APPLIED_INSTRUCTION_SPEC + formatted;
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
