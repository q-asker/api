package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiQuestion;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** GeminiQuestion → AIProblemSet 변환. 문제+선택지+해설 원본 데이터를 매핑한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiQuestionMapper {

  /**
   * questions 목록을 AIProblemSet으로 변환한다. 번호는 소비자 측에서 할당한다.
   *
   * @param questions 파싱된 문제 목록
   * @param referencedPages 참조 페이지 목록
   * @return 변환된 AIProblemSet (해설 원본 데이터 포함)
   */
  public static AIProblemSet toDto(List<GeminiQuestion> questions, List<Integer> referencedPages) {
    List<AIProblem> result =
        questions.stream()
            .map(
                q ->
                    new AIProblem(
                        q.content(),
                        q.quizExplanation(),
                        q.selections() == null
                            ? List.of()
                            : q.selections().stream()
                                .map(
                                    s -> new AISelection(s.content(), s.explanation(), s.correct()))
                                .toList(),
                        referencedPages))
            .toList();

    return new AIProblemSet(result);
  }
}
