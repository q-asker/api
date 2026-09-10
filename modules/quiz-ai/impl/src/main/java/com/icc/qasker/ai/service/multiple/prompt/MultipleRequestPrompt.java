package com.icc.qasker.ai.service.multiple.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** MULTIPLE 퀴즈 전용 유저 프롬프트. 1청크 스트리밍 방식. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MultipleRequestPrompt {

  /** 청크 K(K≥2) 유저 프롬프트 꼬리에 붙는 중복 회피 지침. */
  public static final String DEDUP_INSTRUCTION =
      "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 주제·표현·정답 분포(answerIndex)·Bloom's 하위 과정이"
          + " 겹치지 않게 이번 청크 문항을 작성한다."
          + " stemSummary와 동일·유사한 주제는 다른 각도(다른 패턴 라벨, 다른 강의노트 페이지)로 재구성하고,"
          + " 정답 위치(answerIndex)가 직전 청크와 같은 쪽으로 쏠리지 않게 분산한다.";

  /**
   * 청크 하나를 요청하는 유저 프롬프트. 사용자 지시가 있으면 뒤에 두 조각(반영 규격·override 태그)이 붙고, 없으면 생성 지시만 나간다 — 아래 한 덩어리 안의
   * %s 자리 둘이 그 분기다.
   *
   * <p>다른 유형과 달리 머리 경고(⚠️ 사용자 최우선 지시 존재)를 앞에 두지 않는다 — 이 유형만 앞이 비어 있다.
   *
   * <p>태그명 critical_user_override 는 LLM 이 최우선 지시임을 인식하게 하고, 맨 뒤(recency)에 두어 긴 프롬프트에서 묻히지 않게 한다.
   */
  public static String generate(int quizCount, String customInstruction) {
    boolean hasInstruction = customInstruction != null && !customInstruction.isBlank();

    String appliedInstructionSpec =
        hasInstruction
            ? """
            # 사용자 지시 반영
            - 사용자 지시에 맞는 패턴과 지식 유형을 Step 1-2의 테이블에서 찾아 해당 few-shot을 따른다. 대응 패턴이 없으면 자유롭게 구성한다.

            # 사용자 지시 반영 결과 기록
            - 사용자 지시를 반영한 내용을 `appliedInstruction` 필드에 1~2문장으로 기록한다.
            - 기록 형식: "사용자 지시 '{지시 내용}'을 반영하여 {구체적으로 무엇을 어떻게 바꿨는지}."
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
        [생성 지시]
        - 정확히 %d개의 문제를 생성하세요.
        - 제공된 문서의 내용으로 문제를 출제하세요.
        - **[페이지 번호 규칙]** 본문에 인쇄된 페이지 번호가 있더라도 이를 무시하고, 제공된 파일의 **첫 번째 페이지를 1페이지, 두 번째를 2페이지...**와 같이 순서대로 간주하여 `referencedPages`를 기록하세요.
        - 모든 해설과 근거에서도 이 순서 기반의 페이지 번호(1, 2, 3...)를 사용하세요.%s%s"""
        .formatted(quizCount, appliedInstructionSpec, override);
  }
}
