package com.icc.qasker.quiz.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.GenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;
import com.icc.qasker.quiz.entity.Selection;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private static final int MAX_CONTENT_LENGTH = 20000;
    private final SlackNotifier slackNotifier;
    private final WebClient aiWebClient;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final S3ValidateService s3ValidateService;


    @Override
    public Mono<GenerationResponse> processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        validate(feGenerationRequest);
        return
            Mono.fromRunnable(() -> {
                    s3ValidateService.isCloudFrontUrl(feGenerationRequest.uploadedUrl());
                })
                .then(callAiServer(feGenerationRequest))
                .flatMap(this::saveToDB)
                .map(ps -> new GenerationResponse(
                    hashUtil.encode(ps.getId())
                ))
                .flatMap(ps ->
                    slackNotifier.notifyText("""
                            ✅ [퀴즈 생성 완료 알림]
                            ProblemSet ID: %s
                            """.formatted(
                            ps.getProblemSetId()
                        ))
                        .thenReturn(ps)
                )
                .doOnError(error -> log.error("예외 발생: {}", error.getMessage(), error))
                .onErrorResume(this::unifyError);
    }

    private void validate(FeGenerationRequest feGenerationRequest) {
        String uploadedUrl = feGenerationRequest.uploadedUrl();
        int quizCount = feGenerationRequest.quizCount();
        if (quizCount % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }

        try {
            URL url = new URL(uploadedUrl);
            URL encodedUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(),
                url.getPath());
            HttpURLConnection connection = (HttpURLConnection) encodedUrl.openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
            }
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
    }

    private Mono<GenerationResponse> unifyError(Throwable error) {
        if (error instanceof CustomException) {
            return Mono.error(error);
        }
        return Mono.error(new CustomException(ExceptionMessage.DEFAULT_ERROR));
    }

    private Mono<AiGenerationResponse> callAiServer(FeGenerationRequest feGenerationRequest) {
        return aiWebClient.post()
            .uri("/generation")
            .bodyValue(feGenerationRequest)
            .retrieve()
            .bodyToMono(AiGenerationResponse.class)
            .onErrorMap(this::webClientError); // ok
    }

    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiResponse) {
        return Mono.fromCallable(() -> {
            if (aiResponse == null || aiResponse.getQuiz() == null) {
                throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
            }
            ProblemSet problemSet = new ProblemSet();
            problemSet.setTitle(aiResponse.getTitle());
            List<Problem> problems = new ArrayList<>(); // problems of problem_set
            for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
                Problem problem = createProblemEntity(problemSet, quiz);
                problems.add(problem);
            }
            problemSet.setProblems(problems);
            return problemSetRepository.save(problemSet); // reflect problems of problem_set to DB
        });
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
        String explanationContent = quiz.getExplanation();
        if (explanationContent.length() > MAX_CONTENT_LENGTH) {
            explanationContent = explanationContent.substring(0, MAX_CONTENT_LENGTH);
        }
        explanation.setContent(explanationContent);
        explanation.setProblem(problem);
        problem.setExplanation(explanation);

        // - 5. referencedpages
        List<ReferencedPage> referencedPages = new ArrayList<>();
        for (Integer page : quiz.getReferencedPages()) {
            ReferencedPage rp = new ReferencedPage();
            rp.setPageNumber(page);
            rp.setProblem(problem);
            referencedPages.add(rp);
        }
        problem.setReferencedPages(referencedPages);

        return problem;
    }

    private Throwable webClientError(Throwable error) {
        if (error instanceof WebClientResponseException we) {
            String errorJson = we.getResponseBodyAsString();
            log.error(errorJson);
        }
        // AI Server time out
        if (error instanceof java.util.concurrent.TimeoutException
            || error.getCause() instanceof TimeoutException) {
            return new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
        }
        // AI Server down
        if (error instanceof WebClientRequestException) {
            return new CustomException(ExceptionMessage.AI_SERVER_CONNECTION_FAILED);
        }
        // AI Server too many requests
        if (error instanceof WebClientResponseException.TooManyRequests) {
            return new CustomException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        }
        // rest
        return new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    }
}

