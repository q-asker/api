package com.icc.qasker.quiz.dto.aiRequest;

import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String uploadedUrl,
    int quizCount,
    QuizType quizType,
    List<Integer> pageNumbers
) {

};
