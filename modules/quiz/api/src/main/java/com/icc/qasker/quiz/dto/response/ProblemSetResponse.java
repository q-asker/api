package com.icc.qasker.quiz.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProblemSetResponse {

    private String title;
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
