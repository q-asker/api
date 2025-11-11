package com.icc.qasker.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;

@Getter
public class AiGenerationResponse {

    private String title;

    @NotEmpty(message = "quiz가 null입니다.")
    @Valid
    private List<QuizGeneratedByAI> quiz;

    public AiGenerationResponse(String title, List<QuizGeneratedByAI> quiz) {
        this.title = title;
        this.quiz = quiz;
    }
}
