package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.EssayGradingResult;

/** ESSAY 답안 채점 서비스 인터페이스. */
public interface EssayGradingService {

  /**
   * 서술형 답안을 분석적 루브릭 기반으로 채점한다.
   *
   * @param question 질문문
   * @param modelAnswer 모범답안
   * @param rubric 분석적 루브릭 (explanation 필드)
   * @param studentAnswer 학생 답안
   * @param attemptCount 시도 횟수 (1~4, 피드백 구체성 수준 결정)
   * @return 채점 결과 (요소별 점수 + 종합 피드백)
   */
  EssayGradingResult grade(
      String question, String modelAnswer, String rubric, String studentAnswer, int attemptCount);
}
