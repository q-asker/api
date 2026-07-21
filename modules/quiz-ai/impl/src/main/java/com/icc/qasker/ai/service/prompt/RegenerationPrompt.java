package com.icc.qasker.ai.service.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 생성 게이트에서 미달 판정된 문항을 세션 내에서 1회 재생성하기 위한 유저 프롬프트(퀴즈 타입 무관 공통). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RegenerationPrompt {

  public static String forHeldProblem(String problemJson, String feedback) {
    return "직전에 생성한 아래 문항이 품질 검증에서 미달 판정됐다. 원문(직전 대화의 문서 근거)을 활용해 지적된 문제를 해결한 개선 문항 1개만 생성한다."
        + " 문항 유형·JSON 스키마는 동일하게 유지하고, questions 배열에 개선 문항 1개만 담아 응답한다.\n\n"
        + "# 미달 문항\n"
        + problemJson
        + "\n\n# 미달 사유·개선 지침\n"
        + (feedback == null ? "(사유 미상)" : feedback);
  }
}
