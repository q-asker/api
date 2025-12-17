package com.icc.qasker.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.SpecificExplanationService;
import com.icc.qasker.quiz.dto.request.SpecificExplanationRequest;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI.SelectionsOfAi;
import com.icc.qasker.quiz.dto.response.SpecificExplanationResponse;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.repository.ProblemRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class SpecificExplanationServiceImpl implements SpecificExplanationService {

    private final HashUtil hashUtil;
    private final RestClient aiRestClient;
    private final ProblemRepository problemRepository;

    public SpecificExplanationServiceImpl(
        @Qualifier("aiFindRestClient") RestClient aiRestClient,
        ProblemRepository problemRepository,
        HashUtil hashUtil
    ) {
        this.aiRestClient = aiRestClient;
        this.problemRepository = problemRepository;
        this.hashUtil = hashUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public SpecificExplanationResponse getSpecificExplanation(String encodedProblemSetId,
        int number) {
        Long problemSetId = hashUtil.decode(encodedProblemSetId);
        Problem problem = problemRepository.findByIdProblemSetIdAndIdNumber(problemSetId, number)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        SpecificExplanationRequest aiRequest = new SpecificExplanationRequest(
            problem.getTitle(),
            problem.getSelections().stream().map(selection -> {
                SelectionsOfAi s = new SelectionsOfAi();
                s.setContent(selection.getContent());
                s.setCorrect(selection.isCorrect());
                return s;
            }).toList()
        );
        String aiExplanationRaw = Objects.requireNonNull(
            aiRestClient.post()
                .uri("/specific-explanation")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(aiRequest)
                .retrieve()
                .body(String.class)
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

