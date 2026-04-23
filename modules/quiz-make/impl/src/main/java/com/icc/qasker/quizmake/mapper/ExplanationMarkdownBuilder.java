package com.icc.qasker.quizmake.mapper;

import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 셔플된 선택지 순서에 맞춰 최종 해설 마크다운을 조립한다.
 *
 * <p>bloomsLevel(Bloom's 수준 태그)과 각 selection의 explanation(해설 본문)을 조합한다. 정답/오답 헤더와 선택지 내용은
 * selection.isCorrect()와 selection.getContent()에서 코드로 주입한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExplanationMarkdownBuilder {

  /**
   * 셔플된 선택지 순서로 병합된 해설 마크다운을 조립한다.
   *
   * @param quiz 문제 (셔플된 selections + bloomsLevel)
   * @return 병합된 마크다운 문자열
   */
  public static String build(QuizGeneratedFromAI quiz, Language language) {
    String correctHeader = language == Language.EN ? "## Correct Answer" : "## 정답 선택지";
    String wrongHeader = language == Language.EN ? "## Wrong Answer" : "## 오답 선택지";

    StringBuilder sb = new StringBuilder();

    // 1. Bloom's 수준 태그 + 설명
    if (hasText(quiz.getBloomsLevel())) {
      String raw = quiz.getBloomsLevel().strip();
      sb.append("- **평가 수준**: ").append(raw);
      sb.append("\n\n---\n\n");
    }

    // 2. 선택지별 해설
    if (quiz.getSelections() != null) {
      // 정답 먼저
      for (SelectionsOfAI sel : quiz.getSelections()) {
        if (!sel.isCorrect()) {
          continue;
        }
        sb.append(correctHeader).append("\n\n");
        appendSelectionExp(sb, sel);
      }
      // 그 뒤 오답
      for (SelectionsOfAI sel : quiz.getSelections()) {
        if (sel.isCorrect()) {
          continue;
        }
        sb.append(wrongHeader).append("\n\n");
        appendSelectionExp(sb, sel);
      }
    }

    // 마지막 구분선 제거
    String result = sb.toString();
    if (result.endsWith("---\n\n")) {
      result = result.substring(0, result.length() - "---\n\n".length());
    }
    return result.stripTrailing();
  }

  private static void appendSelectionExp(StringBuilder sb, SelectionsOfAI sel) {
    sb.append("> ").append(sel.getContent()).append("\n\n");
    if (hasText(sel.getExplanation())) {
      sb.append(sel.getExplanation().strip());
      sb.append("\n\n---\n\n");
    }
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
