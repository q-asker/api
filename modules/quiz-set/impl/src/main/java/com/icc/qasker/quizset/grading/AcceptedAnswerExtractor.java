package com.icc.qasker.quizset.grading;

import com.icc.qasker.quizset.entity.Selection;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 정답 선지의 인정 답 목록을 quiz-level로 lift한다 (contract.md §7.2). 저장은 정답 Selection에 얹히지만 전달은 quiz-level
 * {@code acceptedAnswers}이므로, 응답 매퍼가 이 헬퍼로 정답 선지에서 목록을 꺼낸다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptedAnswerExtractor {

  /**
   * 정답 선지의 인정 답 목록. 정답 선지가 없거나 목록이 없으면(비 REAL_BLANK·기존 세트) {@code null} — 프론트는 이 경우 정규화 관용만
   * 적용한다(FR-006).
   */
  public static List<List<String>> fromSelections(List<Selection> selections) {
    if (selections == null) {
      return null;
    }
    for (Selection selection : selections) {
      if (selection.correct()) {
        return selection.acceptedAnswers();
      }
    }
    return null;
  }
}
