package com.icc.qasker;

import com.icc.qasker.domain.enums.DifficultyType;
import com.icc.qasker.domain.enums.QuizType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeGenerationMockRequest {

    @NotBlank(message = "url이 존재하지 않습니다.")
    private String uploadedUrl;
    @Min(value = 5, message = "quizCount는 5이상입니다.")
    @Max(value = 50, message = "quizCount는 50이하입니다.")
    private int quizCount;
    @NotNull(message = "quizType이 null입니다.")
    private QuizType quizType;
    @NotNull(message = "difficultyType가 null입니다.")
    private DifficultyType difficultyType;
    @NotNull(message = "pageNumbers가 null입니다.")
    @Size(min = 1, message = "pageNumbers는 최소 1개 이상이어야 합니다.")
    private List<Integer> pageNumbers;

    public FeGenerationMockRequest(
        String uploadedUrl,
        int quizCount,
        QuizType quizType,
        DifficultyType difficultyType,
        List<Integer> pageNumbers

    ) {
        this.uploadedUrl = uploadedUrl;
        this.quizCount = quizCount;
        this.quizType = quizType;
        this.difficultyType = difficultyType;
        this.pageNumbers = pageNumbers;
    }
}

