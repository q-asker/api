package com.icc.qasker.quizmake.mapper;

import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 에세이 문제의 해설 마크다운을 조립한다.
 *
 * <p>bloomsLevel(Bloom's 수준 태그), 모범답안(correct selection), 분석적 루브릭(explanation)을 조합한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EssayExplanationMarkdownBuilder {

  /**
   * 에세이 문제의 해설 마크다운을 조립한다.
   *
   * @param quiz 문제 (selections에 모범답안 1개 + explanation에 루브릭)
   * @param language 언어
   * @return 병합된 마크다운 문자열
   */
  public static String build(QuizGeneratedFromAI quiz, Language language) {
    String modelAnswerHeader = language == Language.EN ? "## Model Answer" : "## 모범답안";
    String rubricHeader = language == Language.EN ? "## Scoring Rubric" : "## 채점 기준 표";

    StringBuilder sb = new StringBuilder();

    // 1. Bloom's 수준 태그er
    if (hasText(quiz.getBloomsLevel())) {
      String raw = quiz.getBloomsLevel().strip();
      sb.append("- **평가 수준**: ").append(raw);
      sb.append("\n\n---\n\n");
    }

    // 2. 모범답안 (correct selection의 content)
    if (quiz.getSelections() != null) {
      for (SelectionsOfAI sel : quiz.getSelections()) {
        if (!sel.isCorrect()) {
          continue;
        }
        sb.append(modelAnswerHeader).append("\n\n");
        if (hasText(sel.getContent())) {
          sb.append(sel.getContent().strip());
          sb.append("\n\n---\n\n");
        }
        // 에세이는 모범답안 1개만 존재
        break;
      }
    }

    // 3. 분석적 루브릭 (quiz.explanation)
    if (hasText(quiz.getExplanation())) {
      sb.append(rubricHeader).append("\n\n");
      sb.append(quiz.getExplanation().strip());
      sb.append("\n\n");
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
