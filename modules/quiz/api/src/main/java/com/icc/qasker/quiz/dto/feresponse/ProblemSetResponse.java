package com.icc.qasker.quiz.dto.feresponse;

import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProblemSetResponse {

    private String sessionId;
    private String problemSetId;
    private GenerationStatus generationStatus;
    private QuizType quizType;
    private Integer totalCount;
    private List<QuizForFe> quiz;

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
    }
}
