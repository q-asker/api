package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.FeGenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizForFe;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.*;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.SelectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import com.icc.qasker.quiz.repository.ProblemSetRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
@Slf4j
@Service
public class GenerationService {
    private final WebClient aiWebClient;
    private final ProblemSetRepository problemSetRepository;
    private final SelectionRepository selectionRepository;
    private final ProblemRepository problemRepository;

    public GenerationService(
            @Qualifier("aiWebClient") WebClient aiWebClient,
            ProblemSetRepository problemSetRepository,
            SelectionRepository selectionRepository,
            ProblemRepository problemRepository
    ) {
        this.aiWebClient = aiWebClient;
        this.problemSetRepository = problemSetRepository;
        this.selectionRepository = selectionRepository;
        this.problemRepository = problemRepository;
    }
    public Mono<FeGenerationResponse> processGenerationRequest(FeGenerationRequest feGenerationRequest){
        return callAiServer(feGenerationRequest)
                .flatMap(aiResponse -> saveToDB(aiResponse, feGenerationRequest.getQuizCount()))                .map(this::convertToFeResponse)
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
    private Mono<AiGenerationResponse> callAiServer(FeGenerationRequest feGenerationRequest){
        return aiWebClient.post()
                .uri("/generation")
                .bodyValue(feGenerationRequest)
                .retrieve()
                .bodyToMono(AiGenerationResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(this::webClientError); // ok
    }
    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiResponse,int feRequestQuizCount){
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
    private ProblemSet saveProblemSet(AiGenerationResponse aiResponse){
        // for test, private -> public
        // - 1. problem set
        ProblemSet problemSet = new ProblemSet();
        problemSet.setTitle(aiResponse.getTitle());
        problemSet = problemSetRepository.save(problemSet); // generate problem_set_id for a later process
        List<Problem> problems = new ArrayList<>(); // problems of problem_set
        for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
            Problem problem = createProblemEntity(problemSet, quiz);
            problems.add(problem);
        }
        problemSet.setProblems(problems);
        return problemSetRepository.save(problemSet); // reflect problems of problem_set to DB
    }
    private Problem createProblemEntity(ProblemSet problemSet,QuizGeneratedByAI quiz){
        // - 2. problem
        ProblemId problemId = new ProblemId(problemSet.getId(), quiz.getNumber());
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setTitle(quiz.getTitle());
        problem.setProblemSet(problemSet);

        // - 3. selection
        List<Selection> selections = new ArrayList<>();
        for (QuizGeneratedByAI.SelectionWithAnswer sel : quiz.getSelections()) {
            Selection selection = new Selection();
            selection.setContent(sel.getContent());
            selection.setCorrect(sel.isCorrect());
            selection.setProblem(problem);
            selections.add(selection);
        }
        problem.setSelections(selections);

        // - 4. explanation
        Explanation explanation = new Explanation();
        explanation.setId(problemId);
        explanation.setContent(quiz.getExplanation());
        explanation.setProblem(problem);
        problem.setExplanation(explanation);

        return problemRepository.save(problem);
    }
    private FeGenerationResponse convertToFeResponse(ProblemSet problemSet){
        // convert
        List<Problem> savedProblems = problemRepository.findByProblemSet(problemSet);
        List<QuizForFe> quizList = new ArrayList<>();
        for(Problem problem:savedProblems){
            List<Selection> selections = selectionRepository.findByProblem(problem);
            List<QuizForFe.SelectionDto> selectionDtos = new ArrayList<>();

            for (Selection s:selections){
                QuizForFe.SelectionDto selectionDto = new QuizForFe.SelectionDto(s.getId(),s.getContent());
                selectionDtos.add(selectionDto);
            }
            QuizForFe quiz = new QuizForFe(problem.getId().getNumber(),problem.getTitle(),selectionDtos);
            quizList.add(quiz);
        }
        return new FeGenerationResponse(problemSet.getId(),problemSet.getTitle(),quizList);
    }
    // for error
    // - AI Server error
    private Throwable webClientError(Throwable error){
        // AI Server time out
        if (error instanceof java.util.concurrent.TimeoutException || error.getCause() instanceof TimeoutException){
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
