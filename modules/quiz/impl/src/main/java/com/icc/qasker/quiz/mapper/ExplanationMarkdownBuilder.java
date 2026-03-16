package com.icc.qasker.quiz.mapper;

import com.icc.qasker.ai.dto.AIQuizExplanation;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.AISelectionExplanation;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** AIQuizExplanation + List<AISelection> → 최종 마크다운 String 조립. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExplanationMarkdownBuilder {

  /**
   * 구조화된 해설 필드들로부터 최종 마크다운을 조립합니다.
   *
   * @param quizExplanation 문항 전체 해설 (자기 점검, 심화 학습)
   * @param selections AI 응답의 선택지 목록
   * @return 병합된 마크다운 문자열
   */
  public static String build(AIQuizExplanation quizExplanation, List<AISelection> selections) {
    StringBuilder sb = new StringBuilder();

    // 1. 자기 점검 / 심화 학습
    appendQuizExplanation(sb, quizExplanation);

    // 2. 정답 선택지
    if (selections != null) {
      for (AISelection selection : selections) {
        if (selection.correct() && selection.explanation() != null) {
          appendCorrectSelection(sb, selection);
          break;
        }
      }
    }

    // 3. 오답 선택지들
    if (selections != null) {
      int wrongIndex = 1;
      for (AISelection selection : selections) {
        if (!selection.correct() && selection.explanation() != null) {
          appendWrongSelection(sb, selection, wrongIndex);
          wrongIndex++;
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

  private static void appendQuizExplanation(StringBuilder sb, AIQuizExplanation explanation) {
    if (explanation == null) {
      return;
    }

    // 자기 점검 블록
    if (hasText(explanation.selfCheckContent())) {
      sb.append("## 자기 점검");
      if (hasText(explanation.selfCheckLabel())) {
        sb.append(" (").append(explanation.selfCheckLabel()).append(")");
      }
      sb.append("\n\n");
      sb.append(explanation.selfCheckContent().strip()).append("\n\n---\n\n");
    }

    // 심화 학습 블록
    if (hasText(explanation.deepLearningContent())) {
      sb.append("## 심화 학습\n\n");
      sb.append(explanation.deepLearningContent().strip()).append("\n\n---\n\n");
    }
  }

  private static void appendCorrectSelection(StringBuilder sb, AISelection selection) {
    AISelectionExplanation exp = selection.explanation();
    sb.append("## 정답 선택지\n\n");
    sb.append("*").append(selection.content()).append("*\n\n");
    sb.append("### 정답 추론\n\n");

    if (hasText(exp.reasoning())) {
      sb.append(exp.reasoning().strip());
    }
    if (hasText(exp.evidence())) {
      sb.append("\n> 근거: ").append(exp.evidence().strip());
    }

    // 빈칸형 전용: 학습 포인트
    if (hasText(exp.learningPoint())) {
      sb.append("\n\n**학습 포인트** ").append(exp.learningPoint().strip());
      if (hasText(exp.learningPointReview())) {
        sb.append("\n> 복습: ").append(exp.learningPointReview().strip());
      }
    }

    sb.append("\n\n---\n\n");
  }

  private static void appendWrongSelection(StringBuilder sb, AISelection selection, int index) {
    AISelectionExplanation exp = selection.explanation();

    sb.append("## 오답 선택지 ").append(index);
    if (hasText(exp.typeLabel())) {
      sb.append(" — ").append(exp.typeLabel().strip());
    }
    sb.append("\n\n");

    sb.append("*").append(selection.content()).append("*\n\n");
    sb.append("### 오개념 교정\n\n");

    if (hasText(exp.diagnosis())) {
      sb.append("- **진단**: ").append(exp.diagnosis().strip()).append("\n");
    }
    if (hasText(exp.correction())) {
      sb.append("- **교정**: ").append(exp.correction().strip()).append("\n");
    }
    if (hasText(exp.selfCheck())) {
      sb.append("- **스스로 점검**: ").append(exp.selfCheck().strip()).append("\n");
    }
    if (hasText(exp.review())) {
      sb.append("> 복습: ").append(exp.review().strip()).append("\n");
    }

    sb.append("\n---\n\n");
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
