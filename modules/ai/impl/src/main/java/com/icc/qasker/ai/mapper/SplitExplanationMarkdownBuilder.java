package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.structure.GeminiExplanationEntry;
import com.icc.qasker.ai.structure.GeminiQuestionEntry;
import com.icc.qasker.ai.structure.GeminiQuestionSelection;
import com.icc.qasker.ai.structure.GeminiSelectionExplanationEntry;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 스트리밍 분리 응답(GeminiExplanationEntry)에서 최종 마크다운을 조립한다.
 *
 * <p>quizExplanation과 selectionExplanations는 이미 마커 포함 문자열이므로, 정답/오답 헤더와 선택지 내용만 추가하여 연결한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SplitExplanationMarkdownBuilder {

  /**
   * 해설 엔트리와 문제 엔트리로부터 병합된 마크다운을 조립한다.
   *
   * @param explanation 해설 엔트리 (quizExplanation + selectionExplanations)
   * @param question 문제 엔트리 (선택지 content/correct 정보 참조)
   * @return 병합된 마크다운 문자열
   */
  public static String build(GeminiExplanationEntry explanation, GeminiQuestionEntry question) {
    StringBuilder sb = new StringBuilder();

    // 1. 문항 전체 해설 (이미 마커 포함 문자열)
    if (hasText(explanation.quizExplanation())) {
      sb.append(explanation.quizExplanation().strip());
      sb.append("\n\n---\n\n");
    }

    // 2. 선택지별 해설
    if (explanation.selectionExplanations() != null && question != null) {
      List<GeminiQuestionSelection> selections = question.selections();
      for (GeminiSelectionExplanationEntry selExp : explanation.selectionExplanations()) {
        if (selections != null && selExp.index() >= 0 && selExp.index() < selections.size()) {
          GeminiQuestionSelection sel = selections.get(selExp.index());
          String header = sel.correct() ? "## 정답 선택지" : "## 오답 선택지";
          sb.append(header).append("\n\n");
          sb.append("*").append(sel.content()).append("*\n\n");
        }
        if (hasText(selExp.explanation())) {
          sb.append(selExp.explanation().strip());
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
