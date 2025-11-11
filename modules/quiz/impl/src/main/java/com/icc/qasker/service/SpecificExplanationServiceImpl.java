package com.icc.qasker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.SpecificExplanationService;
import com.icc.qasker.dto.request.SpecificExplanationRequest;
import com.icc.qasker.dto.response.QuizGeneratedByAI;
import com.icc.qasker.dto.response.SpecificExplanationResponse;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.repository.ProblemRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class SpecificExplanationServiceImpl implements SpecificExplanationService {

    private final HashUtil hashUtil;
    private final WebClient aiWebClient;
    private final ProblemRepository problemRepository;

    public SpecificExplanationServiceImpl(
        WebClient aiWebClient,
        ProblemRepository problemRepository,
        HashUtil hashUtil
    ) {
        this.aiWebClient = aiWebClient;
        this.problemRepository = problemRepository;
        this.hashUtil = hashUtil;
    }

    @Override
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

        String aiExplanationRaw = Objects.requireNonNull(
            aiWebClient.post()
                .uri("/specific-explanation")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block()
        );
        String explanationText;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(aiExplanationRaw);
            explanationText = node.get("specific_explanation").asText();
        } catch (JsonProcessingException e) {
            log.warn("AI 응답 파싱 실패: 원문 그대로 반환", e);
            explanationText = aiExplanationRaw;
        }

        return new SpecificExplanationResponse(explanationText);

    }

}

