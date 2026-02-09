package com.icc.qasker.quiz.dto.airesponse;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProblemSetGeneratedEvent {

    private List<QuizGeneratedFromAI> quiz;

    @Getter
    @Setter
    public static class QuizGeneratedFromAI {

        private Integer number;
        private String title;
        private List<SelectionsOfAI> selections;
        private String explanation;
        private List<Integer> referencedPages;

        @Getter
        @Setter
        public static class SelectionsOfAI {

            private String content;
            private boolean correct;
        }
    }
}
