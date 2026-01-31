package com.icc.qasker.quiz.dto.aiRequest;

import com.icc.qasker.quiz.dto.feRequest.enums.DifficultyType;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import java.util.List;

public record GenerationRequestToAI(
    String uploadedUrl,
    int quizCount,
    QuizType quizType,
    DifficultyType difficultyType,
    List<Integer> pageNumbers
) {

};
