package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.domain.enums.DifficultyType;
import com.icc.qasker.quiz.domain.enums.QuizType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class FeGenerationRequest {

    @NotBlank(message = "url이 존재하지 않습니다.")
    private String uploadedUrl;
    @Min(value = 5, message = "quizCount는 5이상입니다.")
    @Max(value = 50, message = "quizCount는 50이하입니다.")
    private int quizCount;
    @NotNull(message = "quizType이 null입니다.")
    private QuizType quizType;
    @NotNull(message = "difficultyType가 null입니다.")
    private DifficultyType difficultyType;
    private boolean pageSelected;
    private Integer startPageNumber;
    private Integer endPageNumber;


    public void validateQuizCount() {
        if (quizCount % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }
    }

    public void validatePageSize() {
        if (pageSelected) {
            if (startPageNumber == null || endPageNumber == null) {
                throw new CustomException(ExceptionMessage.INVALID_PAGE_REQUEST);
            }
            if ((endPageNumber < startPageNumber) || endPageNumber - startPageNumber > 100) {
                throw new CustomException(ExceptionMessage.INVALID_PAGE_REQUEST);
            }
        }
    }
}
