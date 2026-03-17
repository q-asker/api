package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiProblemSetMapper {

  /**
   * GeminiProblemSet → AIProblemSet 변환 + 번호 재할당. 구조화된 해설을 마크다운으로 조립하여 String으로 전달합니다.
   *
   * @param source Gemini 응답 역직렬화 결과
   * @param referencedPages 참조 페이지 목록
   * @param numberCounter 스레드 안전 번호 카운터
   * @return 변환된 AIProblemSet
   */
  public static AIProblemSet toDto(
      List<GeminiProblem> source, List<Integer> referencedPages, AtomicInteger numberCounter) {
    List<AIProblem> result = new ArrayList<>(source.size());

    for (GeminiProblem problem : source) {
      List<AISelection> selections = mapSelections(problem);
      int number = numberCounter.getAndIncrement();

      // 구조화된 해설 → 마크다운 조립
      String mergedExplanation =
          ExplanationMarkdownBuilder.build(problem.explanation(), problem.selections());

      result.add(
          new AIProblem(number, problem.content(), mergedExplanation, selections, referencedPages));
    }

    return new AIProblemSet(result);
  }

  private static List<AISelection> mapSelections(GeminiProblem problem) {
    if (problem.selections() == null) {
      return List.of();
    }
    return problem.selections().stream()
        .map(gs -> new AISelection(gs.content(), null, gs.correct()))
        .toList();
  }
}
