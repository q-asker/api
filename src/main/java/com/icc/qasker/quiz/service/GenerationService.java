package com.icc.qasker.quiz.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationService {
    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;
    private final ProblemSetRepository problemSetRepository;
    private final SelectionRepository selectionRepository;
    private final ProblemRepository problemRepository;

    public Mono<FeGenerationResponse> processGenerationRequest(FeGenerationRequest feGenerationRequest){
        return callAiServer(feGenerationRequest)
                .flatMap(this::saveToDB)
                .map(this::convertToFeResponse);
    }
    private Mono<AiGenerationResponse> callAiServer(FeGenerationRequest feGenerationRequest){
        return aiWebClient
                .post()
                .uri("/generation")
                .bodyValue(feGenerationRequest)
                .retrieve()
                .bodyToMono(AiGenerationResponse.class);
    }
    private Mono<ProblemSet> saveToDB(AiGenerationResponse aiResponse){
        return Mono.fromCallable(() -> saveProblemSet(aiResponse));
    }
    private ProblemSet saveProblemSet(AiGenerationResponse aiResponse){
        // generate problem set
        ProblemSet problemSet = new ProblemSet();
        problemSet.setTitle(aiResponse.getTitle());
        problemSet = problemSetRepository.save(problemSet);

        // create problems
        List<Problem> problems = new ArrayList<>();

        for (QuizGeneratedByAI quiz : aiResponse.getQuiz()) {
            Problem problem = createProblemEntity(problemSet, quiz);
            problems.add(problem);
        }
        problemSet.setProblems(problems);

        return problemSetRepository.save(problemSet);
    }
    private Problem createProblemEntity(ProblemSet problemSet,QuizGeneratedByAI quiz){
        //
        ProblemId problemId = new ProblemId(problemSet.getId(), quiz.getNumber());
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setTitle(quiz.getTitle());

        // correctAnswer later

        // set Selections
        List<Selection> selections = new ArrayList<>();
        Selection correctSelection = null;

        List<String> selectionTexts = quiz.getSelections();
        int correctIdx = quiz.getCorrectAnswer();

        for (int i = 0; i < selectionTexts.size(); i++) {
            String content = selectionTexts.get(i);
            Selection selection = new Selection();
            selection.setContent(content);
            selection.setProblem(problem);
            selections.add(selection);
        }

        List<Selection> savedSelections = selectionRepository.saveAll(selections);
        problem.setSelections(savedSelections);

        if (correctIdx >= 0 && correctIdx < savedSelections.size()) {
            correctSelection = savedSelections.get(correctIdx);
            problem.setCorrectAnswer(correctSelection.getId());
        }
        // explanation
        Explanation explanation = new Explanation();
        explanation.setId(problemId);
        explanation.setContent(quiz.getExplanation());
        explanation.setProblem(problem);
        problem.setExplanation(explanation);

        return problem;
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
}
