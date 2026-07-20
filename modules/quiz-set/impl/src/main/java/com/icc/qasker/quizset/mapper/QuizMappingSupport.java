package com.icc.qasker.quizset.mapper;

import com.icc.qasker.quizset.entity.Selection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 문항 매핑 시 공통으로 쓰는 선택지 1-based 변환과 FE 초기 응답 상태 상수를 제공한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuizMappingSupport {

  /** 아직 사용자가 응답하지 않은 상태의 답안 값. */
  static final int UNANSWERED_USER_ANSWER = 0;

  /** 아직 채점되지 않은 상태. */
  static final boolean UNCHECKED = false;

  /** 선택지를 1-based 번호와 함께 매핑한다. */
  static <T> List<T> mapSelections(
      List<Selection> selections, BiFunction<Integer, Selection, T> factory) {
    return IntStream.range(0, selections.size())
        .mapToObj(i -> factory.apply(i + 1, selections.get(i)))
        .toList();
  }
}
