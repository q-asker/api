package com.icc.qasker.quiz.dto.airequest;

import com.icc.qasker.quiz.dto.ferequest.enums.DifficultyType;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String uploadedUrl,
    int quizCount,
    QuizType quizType,
    DifficultyType difficultyType,
    List<Integer> pageNumbers
) {

};
