package com.icc.qasker.quiz.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QuizView {

    private int number;
    private String title;
    private int userAnswer;
    private boolean check;
    private List<SelectionView> selections;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectionView {

        private int id;
        private String content;
        private boolean correct;
    }
}