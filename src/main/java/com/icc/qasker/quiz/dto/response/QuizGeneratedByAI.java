package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class QuizGeneratedByAI {
    private int number;
    private String title;
    private List<String> selections;
    private String explanation;
    private int correctAnswer;
}
