package com.icc.qasker.quiz.dto.response;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import lombok.Getter;

@Getter
public class AiGenerationResponse {

    private String title;
    private List<QuizGeneratedByAI> quiz;

    public AiGenerationResponse(String title, List<QuizGeneratedByAI> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
        for (QuizGeneratedByAI q : quiz) {
            validateQuiz(q);
        }
        this.title = title;
        this.quiz = quiz;
    }

    private void validateQuiz(QuizGeneratedByAI quiz) {
        if (quiz == null) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
        if (quiz.getTitle() == null || quiz.getTitle().trim().isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
        if (quiz.getSelections() == null || quiz.getSelections().size() < 2) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
        if (quiz.getReferencedPages() == null) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
        if (quiz.getExplanation() == null || quiz.getExplanation().trim().isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
        }
    }
}
