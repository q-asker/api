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
    private List<SelectionDto> selections;
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectionDto{
        private Long id;
        private String content;
    }

}
