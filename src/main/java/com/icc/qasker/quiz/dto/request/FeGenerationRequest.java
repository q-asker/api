package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
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
    private boolean pageSelected;
    @NotNull(message = "startPage가 null입니다.")
    @Min(1)
    private int startPage;
    @NotNull(message = "endPage가 null입니다.")
    @Min(1)
    private int endPage;


    public void validateQuizCount() {
        if (quizCount % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }
    }

    public void validatePageSize() {
        if ((endPage < startPage) || (endPage - startPage + 1) > 100) {
            throw new CustomException(ExceptionMessage.INVALID_PAGE_REQUEST);
        }
    }
}
