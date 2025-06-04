package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.*;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GenerationService {

    @NotNull
    @Value("${aws.cloudfront.base-url}")
    private String CLOUDFRONT_BASE_URL;
    private final WebClient aiWebClient;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;

    public GenerationService(
        @Qualifier("aiWebClient") WebClient aiWebClient,
        ProblemSetRepository problemSetRepository,
        HashUtil hashUtil
    ) {
        this.aiWebClient = aiWebClient;
        this.problemSetRepository = problemSetRepository;
        this.hashUtil = hashUtil;
    }

    public Mono<GenerationResponse> processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        return
            Mono.fromRunnable(() -> {
                    if (!feGenerationRequest.getUploadedUrl().startsWith(CLOUDFRONT_BASE_URL)) {
                        throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST);
                    }
                })
                .then(callAiServer(feGenerationRequest))
                .flatMap(aiResponse -> saveToDB(aiResponse, feGenerationRequest.getQuizCount()))
                .map(problemSet -> convertToFeResponse(problemSet.getId()))
                .doOnError(error -> {
                    log.error("예외 발생: {}", error.getMessage(), error);
                })
                .onErrorResume(error -> {
                    if (error instanceof CustomException) {
                        return Mono.error(error);
                    }
                    return Mono.error(new CustomException(ExceptionMessage.DEFAULT_ERROR));
                });
    }

    private Mono<AiGenerationResponse> callAiServer(FeGenerationRequest feGenerationRequest) {
        return aiWebClient.post()
            .uri("/generation")
            .bodyValue(feGenerationRequest)
            .retrieve()
            .bodyToMono(AiGenerationResponse.class)
            .onErrorMap(this::webClientError); // ok
    }

    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiResponse, int feRequestQuizCount) {
        return Mono.fromCallable(() -> {
            if (aiResponse == null || aiResponse.getQuiz() == null) {
                throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
            }
            if (aiResponse.getQuiz().size() != feRequestQuizCount) {
                throw new CustomException(ExceptionMessage.INVALID_AI_RESPONSE);
            }
            return saveProblemSet(aiResponse);
        });
    }

    private ProblemSet saveProblemSet(AiGenerationResponse aiResponse) {
        // for test, private -> public
        // - 1. problem set
        ProblemSet problemSet = new ProblemSet();
        problemSet.setTitle(aiResponse.getTitle());
        List<Problem> problems = new ArrayList<>(); // problems of problem_set
        for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
            Problem problem = createProblemEntity(problemSet, quiz);
            problems.add(problem);
        }
        problemSet.setProblems(problems);
        return problemSetRepository.save(problemSet); // reflect problems of problem_set to DB
    }

    private Problem createProblemEntity(ProblemSet problemSet, QuizGeneratedByAI quiz) {
        // - 2. problem
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setNumber(quiz.getNumber());
        problem.setId(problemId);
        problem.setTitle(quiz.getTitle());
        problem.setProblemSet(problemSet);

        // - 3. selection
        List<Selection> selections = new ArrayList<>();
        for (QuizGeneratedByAI.SelectionsOfAi sel : quiz.getSelections()) {
            Selection selection = new Selection();
            selection.setContent(sel.getContent());
            selection.setCorrect(sel.isCorrect());
            selections.add(selection);
            selection.setProblem(problem);
        }
        problem.setSelections(selections);

        // - 4. explanation
        Explanation explanation = new Explanation();
        explanation.setContent(quiz.getExplanation());
        explanation.setProblem(problem);
        problem.setExplanation(explanation);

        // - 5. referencedpages
        List<ReferencedPage> referencedPages = new ArrayList<>();
        for (Integer page : quiz.getReferencePages()) {
            ReferencedPage rp = new ReferencedPage();
            rp.setPageNumber(page);
            rp.setProblem(problem);
            referencedPages.add(rp);
        }
        problem.setReferencedPages(referencedPages);

        return problem;
    }

    private GenerationResponse convertToFeResponse(Long problemSetId) {
        return GenerationResponse.of(hashUtil.encode(problemSetId));
    }

    // for error
    // - AI Server error
    private Throwable webClientError(Throwable error) {
        // AI Server time out
        if (error instanceof java.util.concurrent.TimeoutException
            || error.getCause() instanceof TimeoutException) {
            return new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
        }
        // AI Server down
        if (error instanceof WebClientRequestException) {
            return new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        }
        // rest
        return new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    }


}
