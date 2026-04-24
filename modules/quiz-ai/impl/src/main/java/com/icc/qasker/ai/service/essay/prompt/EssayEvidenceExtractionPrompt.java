package com.icc.qasker.ai.service.essay.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Pass 1 증거 추출 전용 유저 프롬프트. 질문문 + 루브릭 + 학생 답안을 조합한다 (모범답안 제외). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EssayEvidenceExtractionPrompt {

  /**
   * 증거 추출 유저 프롬프트를 생성한다.
   *
   * @param question 질문문
   * @param rubric 분석적 루브릭 (explanation 필드에서 추출)
   * @param studentAnswer 학생이 제출한 답안
   * @return 조합된 유저 프롬프트
   */
  public static String generate(String question, String rubric, String studentAnswer) {
    return """
        [증거 추출 요청]
        아래의 질문문, 분석적 루브릭, 학생 답안을 참고하여,
        각 채점 요소별로 학생 답안에서 관련 증거를 원문 그대로 추출하세요.

        ---

        ## 질문문
        %s

        ---

        ## 분석적 루브릭 (채점 요소)
        %s

        ---

        ## 학생 답안
        %s

        ---

        각 채점 요소에 대해:
        1. 학생 답안에서 해당 요소와 직접 관련된 문장을 원문 그대로 인용하세요.
        2. 관련 서술이 없으면 quotedEvidence를 빈 문자열로 두세요.
        3. 학생이 다루지 않은 핵심 개념을 missingConcepts에 나열하세요."""
        .formatted(question, rubric, studentAnswer);
  }
}
