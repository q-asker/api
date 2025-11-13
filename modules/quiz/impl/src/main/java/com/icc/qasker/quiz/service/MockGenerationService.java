package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class MockGenerationService {

    private static final int MAX_CONTENT_LENGTH = 20000;
    private final WebClient aiWebClient;
    private final GenerationServiceImpl generationServiceImpl;
    private final HashUtil hashUtil;

    public MockGenerationService(GenerationServiceImpl generationServiceImpl,
        HashUtil hashUtil,
        @Qualifier("aiMockingWebClient") WebClient aiWebClient1) {
        this.generationServiceImpl = generationServiceImpl;
        this.hashUtil = hashUtil;
        this.aiWebClient = aiWebClient1;
    }

    public Mono<GenerationResponse> processGenerationRequest(
        FeGenerationRequest feGenerationRequest) {
        return
            Mono.fromRunnable(() -> {
                })
                .then(callAiServer(feGenerationRequest))
                .flatMap(this::saveToDB)
                .map(ps -> new GenerationResponse(
                    hashUtil.encode(1)
                ))
                .doOnError(error -> log.error("예외 발생: {}", error.getMessage(), error))
                .onErrorResume(generationServiceImpl::unifyError);
    }

    private Mono<AiGenerationResponse> callAiServer(FeGenerationRequest feGenerationRequest) {
        return aiWebClient.post()
            .uri("/generation")
            .bodyValue(feGenerationRequest)
            .retrieve()
            .bodyToMono(AiGenerationResponse.class)
            .onErrorMap(generationServiceImpl::webClientError); // ok
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
            return problemSet;
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
}
