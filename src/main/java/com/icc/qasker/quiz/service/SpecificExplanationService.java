package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.dto.request.SpecificExplanationRequest;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.dto.response.SpecificExplanationResponse;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.repository.ProblemRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class SpecificExplanationService {

    private final HashUtil hashUtil;
    private final WebClient aiWebClient;
    private final ProblemRepository problemRepository;

    public SpecificExplanationService(
        @Qualifier("aiWebClient") WebClient aiWebClient,
        ProblemRepository problemRepository,
        HashUtil hashUtil
    ) {
        this.aiWebClient = aiWebClient;
        this.problemRepository = problemRepository;
        this.hashUtil = hashUtil;
    }

    public SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId,
        int number) {
        Long problemSetId = hashUtil.decode(encodedProblemSetId);
        Problem problem = problemRepository.findByIdProblemSetIdAndIdNumber(problemSetId, number)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        SpecificExplanationRequest aiRequest = new SpecificExplanationRequest(
            problem.getTitle(),
            problem.getSelections().stream().map(selection -> {
                QuizGeneratedByAI.SelectionsOfAi s = new QuizGeneratedByAI.SelectionsOfAi();
                s.setContent(selection.getContent());
                s.setCorrect(selection.isCorrect());
                return s;
            }).toList()
        );
        System.out.printf("problem.getTitle() = %s\n", problem.getTitle());
        
        String aiExplanation = Objects.requireNonNull(
            aiWebClient.post()
                .uri("/specific-explanation")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block()
        );
        return new SpecificExplanationResponse(
            aiExplanation
        );

    }

}
