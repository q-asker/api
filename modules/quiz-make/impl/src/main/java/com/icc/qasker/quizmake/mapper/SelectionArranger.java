package com.icc.qasker.quizmake.mapper;

import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

/**
 * 퀴즈 타입별 선택지 배치 규칙을 캡슐화한다.
 *
 * <ul>
 *   <li>MULTIPLE / BLANK / REAL_BLANK: 선택지 전체 셔플
 *   <li>OX: X 계열이 1번이면 순서를 바꿔 O가 항상 1번이 되도록 정규화
 *   <li>그 외 타입: 원본 순서 유지
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectionArranger {

  public static void arrange(QuizType quizType, List<QuizGeneratedFromAI> quizzes) {
    if (quizType == QuizType.MULTIPLE
        || quizType == QuizType.BLANK
        || quizType == QuizType.REAL_BLANK) {
      shuffleAll(quizzes);
    } else if (quizType == QuizType.OX) {
      normalizeOxOrder(quizzes);
    }
  }

  private static void shuffleAll(List<QuizGeneratedFromAI> quizzes) {
    for (var quiz : quizzes) {
      if (!CollectionUtils.isEmpty(quiz.getSelections())) {
        List<SelectionsOfAI> shuffled = new ArrayList<>(quiz.getSelections());
        Collections.shuffle(shuffled);
        quiz.setSelections(shuffled);
      }
    }
  }

  private static void normalizeOxOrder(List<QuizGeneratedFromAI> quizzes) {
    // OX 선택지 정규화: X 계열이 1번이면 순서 변경 → O가 항상 1번
    for (var quiz : quizzes) {
      var sels = quiz.getSelections();
      if (sels != null
          && sels.size() == 2
          && sels.get(0).getContent() != null
          && sels.get(0).getContent().matches("(?i)^x$")) {

        List<SelectionsOfAI> swapped = new ArrayList<>(2);
        swapped.add(sels.get(1));
        swapped.add(sels.get(0));
        quiz.setSelections(swapped);
      }
    }
  }
}
