package com.icc.qasker.ai.service.essay.prompt;

import com.icc.qasker.ai.structure.GeminiEvidenceExtractionResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** ESSAY 채점 전용 유저 프롬프트. 질문문 + 모범답안 + 루브릭 + 학생 답안을 조합한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EssayGradingRequestPrompt {

  /**
   * 채점 유저 프롬프트를 생성한다.
   *
   * @param question 질문문
   * @param modelAnswer 모범답안 (핵심 요소 포함)
   * @param rubric 분석적 루브릭 (explanation 필드에서 추출)
   * @param studentAnswer 학생이 제출한 답안
   * @param attemptCount 시도 횟수 (1차: 채점만, 2차+: 피드백 포함)
   * @return 조합된 유저 프롬프트
   */
  public static String generate(
      String question, String modelAnswer, String rubric, String studentAnswer, int attemptCount) {
    String instruction =
        attemptCount == 1
            ? "위 분석적 루브릭의 각 요소별로 채점하세요."
            : "위 분석적 루브릭의 각 요소별로 채점하고, 요소별 피드백과 종합 피드백을 작성하세요.";

    return """
        [채점 요청]
        아래의 질문문, 모범답안, 분석적 루브릭, 학생 답안을 참고하여 채점하세요.

        ---

        ## 질문문
        %s

        ---

        ## 모범답안
        %s

        ---

        ## 분석적 루브릭 (채점 기준)
        %s

        ---

        ## 학생 답안
        %s

        ---

        %s"""
        .formatted(question, modelAnswer, rubric, studentAnswer, instruction);
  }

  /**
   * 증거 기반 채점 유저 프롬프트를 생성한다. (Pass 2용)
   *
   * @param question 질문문
   * @param modelAnswer 모범답안
   * @param rubric 분석적 루브릭
   * @param evidence Pass 1에서 추출된 증거
   * @param attemptCount 시도 횟수
   * @return 조합된 유저 프롬프트
   */
  public static String generateWithEvidence(
      String question,
      String modelAnswer,
      String rubric,
      GeminiEvidenceExtractionResponse evidence,
      int attemptCount) {
    String instruction =
        attemptCount == 1
            ? "위 분석적 루브릭의 각 요소별로, 추출된 증거에 근거하여 채점하세요."
            : "위 분석적 루브릭의 각 요소별로, 추출된 증거에 근거하여 채점하고, 요소별 피드백과 종합 피드백을 작성하세요.";

    StringBuilder evidenceSection = new StringBuilder();
    for (var e : evidence.elements()) {
      evidenceSection.append("### ").append(e.element()).append("\n");
      evidenceSection.append("- **인용 증거**: ");
      evidenceSection.append(e.quotedEvidence().isEmpty() ? "(관련 서술 없음)" : e.quotedEvidence());
      evidenceSection.append("\n");
      if (e.missingConcepts() != null && !e.missingConcepts().isEmpty()) {
        evidenceSection.append("- **누락 개념**: ");
        evidenceSection.append(String.join(", ", e.missingConcepts()));
        evidenceSection.append("\n");
      }
      evidenceSection.append("\n");
    }

    return """
        [채점 요청 — 증거 기반]
        아래의 질문문, 모범답안, 분석적 루브릭, 추출된 증거를 참고하여 채점하세요.
        **중요**: 학생 답안 원문이 아닌, 추출된 증거만을 근거로 채점합니다.

        ---

        ## 질문문
        %s

        ---

        ## 모범답안
        %s

        ---

        ## 분석적 루브릭 (채점 기준)
        %s

        ---

        ## 추출된 증거 (학생 답안에서 요소별 인용)
        %s
        ---

        %s"""
        .formatted(question, modelAnswer, rubric, evidenceSection.toString(), instruction);
  }
}
