package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.quiz.domain.enums.DifficultyType;
import com.icc.qasker.quiz.domain.enums.QuizType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

@Getter
public class FeGenerationRequest {

    @NotBlank(message = "url이 존재하지 않습니다.")
    private String uploadedUrl;
    @Min(value = 5, message = "quizCount는 5이상입니다.")
    private int quizCount;
    @NotNull(message = "quizType이 null입니다.")
    private QuizType quizType;
    @NotNull(message = "difficultyType가 null입니다.")
    private DifficultyType difficultyType;
    @NotNull(message = "pageSelected가 null입니다.")
    private boolean pageSelected;
    @NotEmpty(message = "selectedPages가 비어있습니다.")
    private List<@Min(1) Integer> selectedPages;

    public void validateQuizCount() {
        if (quizCount % 5 != 0) {
            throw new IllegalArgumentException("quizCount는 5배수입니다.");
        }
    }
}
