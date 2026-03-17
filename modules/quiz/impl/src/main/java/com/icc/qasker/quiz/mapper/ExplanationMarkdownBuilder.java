package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 셔플된 선택지 순서에 맞춰 최종 해설 마크다운을 조립한다.
 *
 * <p>quizExplanation(문항 전체 해설)과 각 selection의 explanation(해설 본문)을 조합한다. 정답/오답 헤더와 선택지 내용은
 * selection.isCorrect()와 selection.getContent()에서 코드로 주입한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExplanationMarkdownBuilder {

  /**
   * 셔플된 선택지 순서로 병합된 해설 마크다운을 조립한다.
   *
   * @param quiz 문제 (셔플된 selections + quizExplanation)
   * @return 병합된 마크다운 문자열
   */
  public static String build(QuizGeneratedFromAI quiz) {
    StringBuilder sb = new StringBuilder();

    // 1. 문항 전체 해설 (자기 점검 + 심화 학습)
    if (hasText(quiz.getQuizExplanation())) {
      sb.append(quiz.getQuizExplanation().strip());
      sb.append("\n\n---\n\n");
    }

    // 2. 선택지별 해설 (셔플된 순서대로)
    if (quiz.getSelections() != null) {
      for (SelectionsOfAI sel : quiz.getSelections()) {
        String header = sel.isCorrect() ? "## 정답 선택지" : "## 오답 선택지";
        sb.append(header).append("\n\n");
        sb.append("*").append(sel.getContent()).append("*\n\n");
        if (hasText(sel.getExplanation())) {
          sb.append(sel.getExplanation().strip());
          sb.append("\n\n---\n\n");
        }
      }
    }

    // 마지막 구분선 제거
    String result = sb.toString();
    if (result.endsWith("---\n\n")) {
      result = result.substring(0, result.length() - "---\n\n".length());
    }
    return result.stripTrailing();
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
