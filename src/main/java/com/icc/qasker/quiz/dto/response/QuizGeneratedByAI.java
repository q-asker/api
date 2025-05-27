package com.icc.qasker.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
public class QuizGeneratedByAI {
    private int number;
    private String title;
    private List<SelectionWithAnswer> selections;
    private String explanation;
    @Getter
    public static class SelectionWithAnswer {
        private String content;
        @JsonProperty("isCorrect")
        private boolean correct;
    }
}
