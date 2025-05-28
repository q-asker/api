package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QuizForFe {
    private int number;
    private String title;
    private int userAnswer;
    private boolean check;
    private List<SelectionsForFE> selections;
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectionsForFE{
        private int id;
        private String content;
        private boolean correct;
    }

}
