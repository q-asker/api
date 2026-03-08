package com.icc.qasker.quiz.mapper;

import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.ferequest.GenerationRequest;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FeRequestToAIRequestMapper {

    public static GenerationRequestToAI toAIRequest(
        GenerationRequest fe, Consumer<AIProblemSet> consumer) {
        return new GenerationRequestToAI(
            fe.uploadedUrl(),
            fe.quizType().name(),
            fe.quizCount(),
            fe.pageNumbers(),
            consumer
        );
    }
}