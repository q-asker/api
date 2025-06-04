package com.icc.qasker.quiz.dto.response;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AiGenerationResponse {

    @NotBlank(message = "title이 존재하지 않습니다.")
    private String title;

    @NotEmpty(message = "quiz가 null입니다.")
    @Valid
    private List<QuizGeneratedByAI> quiz;

    public AiGenerationResponse(String title, List<QuizGeneratedByAI> quiz) {
        this.title = title;
        this.quiz = quiz;
    }
}
