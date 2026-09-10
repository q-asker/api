package com.icc.qasker.quizmake.service.generation;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 저장 직전에 선지 순서를 확정한다. 저장 순서가 곧 사용자가 보는 순서이고, 선지에 인라인으로 붙는 해설의 정렬 기준이기도 하므로 해설을 조립하기 전에 끝내야 한다.
 *
 * <p>AI 가 만든 내용 자체는 건드리지 않는다 — 무엇을 물을지는 생성(quiz-ai)의 몫이고, 어떤 순서로 보여줄지는 여기(quiz-make)의 몫이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectionArrangement {

  /** 유형에 맞는 순서로 선지를 재배치한다. 선지가 없는 유형(REAL_BLANK·ESSAY)은 그대로 둔다. */
  public static void arrange(QuizGeneratedFromAI quiz, QuizType quizType) {
    List<SelectionsOfAI> selections = quiz.getSelections();
    if (selections == null || selections.isEmpty()) {
      return;
    }
    switch (quizType) {
      case MULTIPLE, BLANK -> quiz.setSelections(shuffle(selections));
      case OX -> quiz.setSelections(normalizeOxOrder(selections));
      case REAL_BLANK, ESSAY -> {
        // 선지가 정답 보존용이라 순서 개념이 없다.
      }
    }
  }

  /** MULTIPLE·BLANK: 선지 전체 셔플(정답 위치 무작위화). */
  private static List<SelectionsOfAI> shuffle(List<SelectionsOfAI> selections) {
    List<SelectionsOfAI> copy = new ArrayList<>(selections);
    Collections.shuffle(copy);
    return copy;
  }

  /** OX: X 계열이 1번이면 순서를 바꿔 O 가 항상 1번이 되도록 정규화. */
  private static List<SelectionsOfAI> normalizeOxOrder(List<SelectionsOfAI> selections) {
    if (selections.size() != 2) {
      return selections;
    }
    SelectionsOfAI first = selections.get(0);
    if (first.getContent() != null && first.getContent().matches("(?i)^x$")) {
      return List.of(selections.get(1), selections.get(0));
    }
    return selections;
  }
}
