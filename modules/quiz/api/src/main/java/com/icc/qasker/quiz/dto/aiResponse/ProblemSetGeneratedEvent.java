package com.icc.qasker.quiz.dto.aiResponse;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ProblemSetGeneratedEvent implements StreamEvent {

    private List<QuizGeneratedFromAI> quiz;

    @Getter
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
