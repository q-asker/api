package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.global.quiz.QuizType;
import java.util.List;

/**
 * 오답 모아풀기 결과. 모을 오답이 없거나 상한에 걸리거나 일부 유형이 실패해도 실패 응답이 아니다 — 사용자에게 알려야 할 상태이지 요청의 실패가 아니고, 화면 문구는 이
 * 사실들로 프론트가 만든다.
 *
 * @param createdSets 만들어진 문제집. 유형마다 하나이며, 하나도 없으면 빈 목록이다.
 * @param excludedEssayCount 수집에서 빠진 서술형 문항 수. 서술형은 정오답이 규칙으로 갈리지 않아 "틀린 수"가 아니라 "제외된 수"다.
 * @param deletedSourceCount 원본 문제집이 이미 지워져 건너뛴 기록 수.
 * @param failedTypes 만들다 실패한 유형. 성공한 문제집은 그대로 남는다.
 * @param emptyReason 만들어진 문제집이 하나도 없을 때의 사유. 하나라도 있으면 null.
 */
public record WrongAnswerSetResponse(
    List<CreatedSet> createdSets,
    int excludedEssayCount,
    int deletedSourceCount,
    List<QuizType> failedTypes,
    EmptyReason emptyReason) {

  /**
   * @param truncated 상한에 걸려 일부만 담겼는지. 걸리지 않은 유형은 false다.
   */
  public record CreatedSet(
      String problemSetId,
      String historyId,
      QuizType quizType,
      String title,
      int questionCount,
      boolean truncated) {}

  /** 모을 것이 없던 이유. 판정 우선순위는 선언 순서와 같다. */
  public enum EmptyReason {
    /** 폴더에 끝까지 푼 기록이 하나도 없다. */
    NO_HISTORY,
    /** 수집할 수 있는 기록이 서술형뿐이었다. */
    ESSAY_ONLY,
    /** 기록은 있었으나 원본 문제집이 모두 지워졌다. */
    SOURCE_DELETED,
    /** 수집 대상은 있었고 틀린 문항이 하나도 없었다. */
    ALL_CORRECT
  }
}
