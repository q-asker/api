package com.icc.qasker.quizhistory.service.wronganswer;

import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 문제집 제목에 쓰는 유형 이름. 사용자가 문제를 만들 때 옵션 화면에서 고른 바로 그 단어를 쓴다 — 목록의 제목과 화면의 유형 표기가 다른 말을 하지 않게 하려는 것이다.
 * 두 빈칸 유형이 같은 이름을 쓰면 유형별로 나뉘어 만들어진 문제집을 서로 구별할 수 없다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuizTypeLabel {

  static String of(QuizType quizType) {
    return switch (quizType) {
      case MULTIPLE -> "객관식";
      case OX -> "OX 퀴즈";
      case BLANK -> "빈칸 넣기";
      case REAL_BLANK -> "빈칸 직접입력";
      case ESSAY -> "서술형";
    };
  }
}
