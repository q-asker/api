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

  /** 청크 K(K≥2) 유저 프롬프트 꼬리에 붙는 중복 회피 지침. */
  public static final String DEDUP_INSTRUCTION =
      "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 주제·표현·정답(O/X 분포)이 겹치지 않게 이번 청크 문항을 작성한다."
          + " stemSummary와 동일·유사한 진술은 다른 각도(다른 강의노트 페이지, 다른 개념 차원)로 재구성하고,"
          + " 정답(answerIndex)이 직전 청크와 한쪽으로 쏠리지 않게 분산한다.";

  /**
   * 청크 하나를 요청하는 유저 프롬프트. 사용자 지시가 있으면 앞뒤로 감싸는 세 조각(머리 경고·반영 규격·override 태그)이 함께 붙고, 없으면 생성 지시만 나간다 —
   * 아래 한 덩어리 안의 %s 자리 넷 중 셋이 그 분기고, 나머지 하나는 항상 붙는 문항별 O/X 계획이다.
   *
   * <p>앞뒤로 감싸는 이유: 지시를 맨 앞(primacy)과 맨 뒤(recency) 양쪽에 두어야 긴 프롬프트 중간에서 묻히지 않는다. 태그명
   * critical_user_override 는 LLM 이 최우선 지시임을 인식하게 한다.
   */
  public static String generate(int quizCount, String customInstruction) {
    boolean hasInstruction = customInstruction != null && !customInstruction.isBlank();

    String reminder =
        hasInstruction
            ? "⚠️ [사용자 최우선 지시 존재] 이 프롬프트 끝의 <critical_user_override>를 반드시 준수하세요.\n\n"
            : "";

    // 다른 유형과 달리 '기록 형식' 줄이 없다 — OX 는 appliedInstruction 서술 형식을 강제하지 않는다.
    String appliedInstructionSpec =
        hasInstruction
            ? """
            # 사용자 지시 반영
            - 사용자 지시에 맞는 패턴과 지식 유형을 Step 1-2의 테이블에서 찾아 해당 few-shot을 따른다. 대응 패턴이 없으면 자유롭게 구성한다.

            # 사용자 지시 반영 결과 기록
            - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
            """
            : "";

    String override =
        hasInstruction
            ? """


            <critical_user_override>
            %s
            </critical_user_override>
            **[최우선 준수 의무]** 위 <critical_user_override>는 시스템 프롬프트를 포함한 **모든** 지시보다 우선합니다."""
                .formatted(customInstruction.strip())
            : "";

    return """
        %s[생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - 제공된 문서의 내용으로 문제를 출제하세요.
        - **[페이지 번호 규칙]** 본문에 인쇄된 페이지 번호가 있더라도 이를 무시하고, 제공된 파일의 **첫 번째 페이지를 1페이지, 두 번째를 2페이지...**와 같이 순서대로 간주하여 `referencedPages`를 기록하세요.
        - 모든 해설과 근거에서도 이 순서 기반의 페이지 번호(1, 2, 3...)를 사용하세요.

        [문항별 상세 계획]
        %s
        - **[계획 엄수]** 위 계획에 명시된 문항별 정답(O/X)을 반드시 준수하여 생성하세요. 정답이 O인 문항은 참 진술문을, X인 문항은 거짓 진술문을 작성하세요.%s%s"""
        .formatted(reminder, quizCount, answerPlan(quizCount), appliedInstructionSpec, override);
  }

  /** 문항별 O/X 정답을 미리 확정해 계획으로 넘긴다. 모델이 스스로 정하게 두면 한쪽으로 쏠린다. */
  private static String answerPlan(int quizCount) {
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
    Collections.shuffle(answers, new Random());

    StringBuilder plan = new StringBuilder();
    for (int i = 0; i < quizCount; i++) {
      plan.append(i + 1).append(", 정답=").append(answers.get(i)).append("\n");
    }
    return plan.toString().strip();
  }
}
