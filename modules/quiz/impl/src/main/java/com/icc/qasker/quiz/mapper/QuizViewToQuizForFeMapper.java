package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe.SelectionForFE;
import com.icc.qasker.quiz.view.QuizView;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QuizViewToQuizForFeMapper {

    public static QuizForFe toQuizForFe(QuizView quizView) {
        List<SelectionForFE> selections = quizView.getSelections().stream()
            .map(selectionView -> {
                return new SelectionForFE(
                    selectionView.getId(),
                    selectionView.getContent(),
                    selectionView.isCorrect()
                );
            })
            .toList();

        return new QuizForFe(
            quizView.getNumber(),
            quizView.getTitle(),
            0,
            false,
            selections
        );
    }
}
