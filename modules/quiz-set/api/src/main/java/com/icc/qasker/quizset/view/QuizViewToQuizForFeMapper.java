package com.icc.qasker.quizset.view;

import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe.SelectionForFE;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QuizViewToQuizForFeMapper {

  public static QuizForFe toQuizForFe(QuizView quizView) {
    List<SelectionForFE> selections =
        quizView.getSelections().stream()
            .map(
                selectionView -> {
                  return new SelectionForFE(
                      selectionView.getId(),
                      selectionView.getContent(),
                      null,
                      selectionView.isCorrect());
                })
            .toList();

    // 생성 진행 SSE 스트리밍 미리보기 경로. 허용답안은 완성 문항 조회(GET /problem-set/{id})에서 내려가므로 여기선 생략(null).
    return new QuizForFe(
        quizView.getNumber(),
        quizView.getTitle(),
        0,
        false,
        selections,
        null,
        quizView.getAppliedInstruction(),
        null);
  }
}
