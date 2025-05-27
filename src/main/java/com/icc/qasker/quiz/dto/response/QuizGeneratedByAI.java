package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@AllArgsConstructor
@Setter
public class QuizGeneratedByAI {
    private int number;
    private String title;
    private List<SelectionWithAnswer> selections;
    private String explanation;
    @Getter
    @AllArgsConstructor
    @Setter
    public static class SelectionWithAnswer {
        private String content;
        private boolean isCorrect;
    }
}
