package com.icc.qasker.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.dto.aiResponse.GenerationResponseFromAI;
import com.icc.qasker.quiz.dto.aiResponse.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final ProblemSetResponseMapper problemSetResponseMapper;
    private final SlackNotifier slackNotifier;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final ProblemRepository problemRepository;
    private final RestClient aiStreamClient;
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter processGenerationRequest(
        GenerationRequest request, String userId) {

        SseEmitter emitter = new SseEmitter(110 * 1000L);

        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
        ProblemSet save = problemSetRepository.save(problemSet);
        String id = hashUtil.encode(save.getId());
        Thread.ofVirtual().start(() -> {
            try {
                aiStreamClient.post()
                    .uri("/generation")
                    .body(request)
                    .accept(MediaType.APPLICATION_NDJSON)
                    .exchange((req, res) -> {
                        while (true) {
                            BufferedReader br = new BufferedReader(
                                new InputStreamReader(res.getBody(), StandardCharsets.UTF_8));
                            String line = br.readLine();
                            if (line == null) {
                                break;
                            }
                            List<QuizForFe> quizForFeList = processAIResponse(line, problemSet);
                            emitter.send(
                                new ProblemSetResponse(
                                    id,
                                    request.quizCount(),
                                    quizForFeList
                                ));
                        }
                        return null;
                    });

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
                throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
            }
        });
        return emitter;
    }

    private List<QuizForFe> processAIResponse(String line, ProblemSet problemSet)
        throws JsonProcessingException {
        List<Problem> problems = new ArrayList<>();
        List<QuizForFe> quizForFeList = new ArrayList<>();
        GenerationResponseFromAI dto = objectMapper.readValue(line,
            GenerationResponseFromAI.class);
        for (QuizGeneratedFromAI quizGeneratedFromAI : dto.getQuiz()) {
            Problem problem = Problem.of(quizGeneratedFromAI, problemSet);
            problems.add(problem);
            quizForFeList.add(problemSetResponseMapper.fromEntity(problem));
        }
        problemRepository.saveAll(problems);
        return quizForFeList;
    }
}
