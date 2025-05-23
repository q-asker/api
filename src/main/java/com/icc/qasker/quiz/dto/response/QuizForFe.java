package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@Getter
@AllArgsConstructor
public class QuizForFe {
    private int number;
    private String title;
    private List<SelectionDto> selections;

    public static class SelectionDto{
        private Long id;
        private String content;
    }

}
