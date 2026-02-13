package com.icc.qasker.quiz.mapper;

import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FeRequestToAIRequestMapper {
    // TODO: mapper interface를 이용한 각 mapper 빈 교체

    public GenerationRequestToAI toAIRequest(GenerationRequest fe) {
        return GenerationRequestToAI.builder()
            .uploadedUrl(fe.uploadedUrl())
            .quizCount(fe.quizCount())
            .quizType(fe.quizType())
            .difficultyType(fe.difficultyType())
            .pageNumbers(fe.pageNumbers())
            .build();
    }
}
