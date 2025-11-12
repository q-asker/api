package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.quiz.dto.request.enums.DifficultyType;
import com.icc.qasker.quiz.dto.request.enums.QuizType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FeGenerationRequest(

    @NotBlank(message = "url이 존재하지 않습니다.")
    String uploadedUrl,
    @Min(value = 5, message = "quizCount는 5이상입니다.")
    @Max(value = 50, message = "quizCount는 50이하입니다.")
    int quizCount,
    @NotNull(message = "quizType이 null입니다.")
    QuizType quizType,
    @NotNull(message = "difficultyType가 null입니다.")
    DifficultyType difficultyType,
    @NotNull(message = "pageNumbers가 null입니다.")
    @Size(min = 1, message = "pageNumbers는 최소 1개 이상이어야 합니다.")
    List<Integer> pageNumbers

) {

};
