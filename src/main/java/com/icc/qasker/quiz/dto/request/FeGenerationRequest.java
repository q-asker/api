package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.quiz.domain.enums.QuizType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeGenerationRequest {
    private String uploadedUrl;
    private int quizCount;
    private QuizType type;
}
