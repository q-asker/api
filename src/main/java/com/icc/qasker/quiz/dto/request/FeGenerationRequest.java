package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.domain.enums.DifficultyType;
import com.icc.qasker.quiz.domain.enums.QuizType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;

@Getter
public class FeGenerationRequest {


    private String uploadedUrl;
    private int quizCount;
    private QuizType quizType;
    private DifficultyType difficultyType;

    public FeGenerationRequest (String uploadedUrl, int quizCount, QuizType quizType, DifficultyType difficultyType){
        if (uploadedUrl == null && quizCount <= 0 && quizType == null && difficultyType == null) {
            throw new CustomException(ExceptionMessage.NULL_GENERATION_REQUEST);
        }
        if (uploadedUrl == null || uploadedUrl.trim().isEmpty()
                || quizCount <= 0 || quizCount % 5 != 0
                || quizType == null || difficultyType == null) {
            throw new CustomException(ExceptionMessage.INVALID_FE_REQUEST);
        }


        this.uploadedUrl = uploadedUrl;
        this.quizCount = quizCount;
        this.quizType = quizType;
        this.difficultyType = difficultyType;

    }
}
