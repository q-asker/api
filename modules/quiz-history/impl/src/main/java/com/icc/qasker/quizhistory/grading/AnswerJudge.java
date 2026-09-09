package com.icc.qasker.quizhistory.grading;

import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import com.icc.qasker.quizset.grading.RealBlankGrader;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 저장된 답안 스냅샷으로 문항별 정오답을 되짚는 판정기(SSOT). 정오답은 저장되지 않고(기록에는 답안과 총점만 남는다) 조회 시마다 다시 판정하므로, 기록 상세와 오답
 * 수집이 같은 함수를 거쳐 동일한 답을 얻어야 한다.
 *
 * <p>ESSAY는 규칙으로 정오답이 갈리지 않아(AI 채점 점수만 있다) 여기서 다루지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnswerJudge {

  /** 정답 선택지의 1-based 위치. 정답 선택지가 없으면 어떤 답과도 같지 않도록 -1. */
  public static int correctIndex(List<SelectionDetail> selections) {
    if (selections == null) {
      return -1;
    }
    for (int i = 0; i < selections.size(); i++) {
      if (selections.get(i).correct()) {
        return i + 1;
      }
    }
    return -1;
  }

  /**
   * REAL_BLANK는 텍스트 정규화 + 인정 집합 멤버십으로, 그 외 유형은 정답 인덱스와 사용자 답을 비교해 판정한다. 미응답(userAnswer=0)은 어떤 정답
   * 인덱스와도 같지 않아 오답이 된다.
   */
  public static boolean isCorrect(
      QuizType quizType, ProblemDetail problem, AnswerSnapshotView answers) {
    if (quizType == QuizType.REAL_BLANK) {
      return RealBlankGrader.grade(problem.selections(), answers.textAnswer(problem.number()))
          .isCorrect();
    }
    return answers.userAnswer(problem.number()) == correctIndex(problem.selections());
  }
}
