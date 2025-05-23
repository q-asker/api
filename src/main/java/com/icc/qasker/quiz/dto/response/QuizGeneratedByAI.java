package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@Getter
@AllArgsConstructor
public class QuizGeneratedByAI {
    private int number;
    private String title;
    private List<String> selections;
    private String explanation;
}
