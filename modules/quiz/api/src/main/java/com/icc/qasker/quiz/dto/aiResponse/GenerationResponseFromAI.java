package com.icc.qasker.quiz.dto.aiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResponseFromAI {

    @NotEmpty(message = "quiz가 null입니다.")
    @Valid
    private List<QuizGeneratedFromAI> quiz;
}
