package com.icc.qasker.quiz.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerationResponse {

    private String title;

    @NotEmpty(message = "quiz가 null입니다.")
    @Valid
    private List<QuizGeneratedByAI> quiz;
}
