package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiQuestionEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** GeminiQuestionEntry → AIProblemSet 변환. 해설 없이 문제+선택지만 매핑한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiQuestionMapper {

  /**
   * questions 목록을 AIProblemSet으로 변환하고, 전역 번호를 할당한다.
   *
   * @param questions 파싱된 문제 목록
   * @param referencedPages 참조 페이지 목록
   * @param numberCounter 스레드 안전 전역 번호 카운터
   * @param numberMapping chunk-local number → global number 매핑 저장소
   * @return 변환된 AIProblemSet (explanation=null)
   */
  public static AIProblemSet toDto(
      List<GeminiQuestionEntry> questions,
      List<Integer> referencedPages,
      AtomicInteger numberCounter,
      Map<Integer, Integer> numberMapping) {
    List<AIProblem> result = new ArrayList<>(questions.size());

    for (GeminiQuestionEntry q : questions) {
      int globalNumber = numberCounter.getAndIncrement();
      numberMapping.put(q.number(), globalNumber);

      List<AISelection> selections =
          q.selections() == null
              ? List.of()
              : q.selections().stream()
                  .map(s -> new AISelection(s.content(), null, s.correct()))
                  .toList();

      result.add(new AIProblem(globalNumber, q.content(), null, selections, referencedPages));
    }

    return new AIProblemSet(result);
  }
}
