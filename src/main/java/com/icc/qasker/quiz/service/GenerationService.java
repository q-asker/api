package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.domain.enums.QuizType;
import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.FeGenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizForFe;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.*;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.SelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import com.icc.qasker.quiz.repository.ProblemSetRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class GenerationService {
    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;
    private final ProblemSetRepository problemSetRepository;
    private final SelectionRepository selectionRepository;
    private final ProblemRepository problemRepository;

    public Mono<FeGenerationResponse> processGenerationRequest(FeGenerationRequest feGenerationRequest){
        return Mono.fromRunnable(() -> validateGenerationRequest(feGenerationRequest))
                .then(callAiServer(feGenerationRequest))
                .flatMap(this::saveToDB)
                .map(this::convertToFeResponse)
                .doOnError(error -> {
                    System.err.println("예외 발생: " + error.getMessage());
                    error.printStackTrace();
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
    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiResponse){
        return Mono.fromCallable(() -> {
            validateAiResponse(aiResponse);
            return saveProblemSet(aiResponse);
        });
    }
    private ProblemSet saveProblemSet(AiGenerationResponse aiResponse){
        // for test, private -> public
        // - problem set
        ProblemSet problemSet = new ProblemSet(); // generate id
        problemSet.setTitle(aiResponse.getTitle()); // set title
        problemSet = problemSetRepository.save(problemSet);

        // - create problems
        List<Problem> problems = new ArrayList<>();

        for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
            Problem problem = createProblemEntity(problemSet, quiz);
            problems.add(problem);
        }
        problemSet.setProblems(problems);

        return problemSetRepository.save(problemSet);
    }
    private Problem createProblemEntity(ProblemSet problemSet,QuizGeneratedByAI quiz){
        // problem
        ProblemId problemId = new ProblemId(problemSet.getId(), quiz.getNumber());
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setTitle(quiz.getTitle());
        problem.setProblemSet(problemSet);
        problem = problemRepository.save(problem); // generate id

        // selection
        List<Selection> selections = new ArrayList<>();
        List<String> selectionTexts = quiz.getSelections();
        for (int i=0;i<selectionTexts.size();i++){
            String content = selectionTexts.get(i);
            Selection selection = new Selection();
            selection.setContent(content);

            selection.setProblem(problem);
            selections.add(selection);

        }
        List<Selection> savedSelections = selectionRepository.saveAll(selections);

        // set correct_answer of problem
        int correctAnswerIndex = quiz.getCorrectAnswer();
        if(correctAnswerIndex >= 0 && correctAnswerIndex < savedSelections.size()) {
            Selection correctSelection = savedSelections.get(correctAnswerIndex);
            problem.setCorrectAnswer(correctSelection.getId());
        }
        problem.setSelections(savedSelections);

        // explanation
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

    // Invalidate response of AI Server
    private void validateAiResponse(AiGenerationResponse aiResponse) {
        if (aiResponse == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        if (aiResponse.getQuiz() == null || aiResponse.getQuiz().isEmpty()) {
            throw new CustomException(ExceptionMessage.EMPTY_QUIZ_LIST);
        }
        for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
            validateQuiz(quiz);
        }
    }
    private void validateQuiz(QuizGeneratedByAI quiz) {
        if (quiz == null) {
            throw new CustomException(ExceptionMessage.NULL_QUIZ);
        }
        if (quiz.getTitle() == null || quiz.getTitle().trim().isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_TITLE);
        }
        if (quiz.getSelections() == null || quiz.getSelections().size() < 2) {
            throw new CustomException(ExceptionMessage.INVALID_SELECTIONS);
        }
        if (quiz.getCorrectAnswer() < 0 || quiz.getCorrectAnswer() >= quiz.getSelections().size()) {
            throw new CustomException(ExceptionMessage.INVALID_CORRECT_ANSWER_INDEX);
        }
        if (quiz.getExplanation() == null || quiz.getExplanation().trim().isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_EXPLANATION);
        }
    }
    // Invalidate request of FE Server
    private void validateGenerationRequest(FeGenerationRequest request) {
        if (request.getUploadedUrl() == null && request.getQuizCount() <= 0 && request.getType() ==null) {
            throw new CustomException(ExceptionMessage.NULL_GENERATION_REQUEST);
        }
        if (request.getUploadedUrl() == null || request.getUploadedUrl().trim().isEmpty()) {
            throw new CustomException(ExceptionMessage.INVALID_URL);
        }
        if (request.getQuizCount() <= 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUESTION_COUNT);
        }
        if (request.getType() == null) {
            throw new CustomException(ExceptionMessage.INVALID_TYPE);
        }
    }

}
