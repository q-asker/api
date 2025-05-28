package com.icc.qasker.quiz.dto.response;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.Selection;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProblemSetResponse {

    private Long problemSetId;
    private String title;
    private List<QuizForFe> quiz;


    public static ProblemSetResponse of(ProblemSet problemSet) {
        List<QuizForFe> quizzes = problemSet.getProblems().stream()
            .map(QuizForFe::of)
            .toList();

        return new ProblemSetResponse(
            problemSet.getId(),
            problemSet.getTitle(),
            quizzes
        );
    }


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizForFe {

        private int number;
        private String title;
        private int userAnswer;
        private boolean check;
        private List<SelectionsForFE> selections;

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SelectionsForFE {

            private int id;
            private String content;
            private boolean correct;
        }

        public static QuizForFe of(Problem problem) {
            return new QuizForFe(
                problem.getId().getNumber(),
                problem.getTitle(),
                0,
                false,
                IntStream.range(0, problem.getSelections().size())
                    .mapToObj(i -> {
                        Selection selection = problem.getSelections().get(i);
                        return new SelectionsForFE(
                            i + 1,
                            selection.getContent(),
                            selection.isCorrect()
                        );
                    })
                    .toList()
            );
        }

    }
}
