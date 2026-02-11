package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public final class FeRequestToAIRequestMapper {

    public static GenerationRequestToAI toAIRequest(GenerationRequest fe) {
        return GenerationRequestToAI.builder()
            .uploadedUrl(fe.uploadedUrl())
            .quizCount(fe.quizCount())
            .quizType(fe.quizType())
            .difficultyType(fe.difficultyType())
            .pageNumbers(fe.pageNumbers())
            .build();
    }
}
