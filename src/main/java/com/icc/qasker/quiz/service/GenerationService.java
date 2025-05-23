package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.FeGenerationResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GenerationService {
    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;

    public Mono<FeGenerationResponse> generateQuiz(FeGenerationRequest feGenerationRequest){

    }
    private Mono<AiGenerationResponse> callAiServer(FeGenerationResponse feGenerationResponse){
        return aiWebClient
                .post()
                .uri("/generation")
                .bodyValue(feGenerationResponse)
                .retrieve()
                .bodyToMono((AiGenerationResponse.class);
    }
    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiGenerationResponse){

    }
}
