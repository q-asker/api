package com.icc.qasker.quiz.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Primary
@Profile("mock")
public class MockAIServerAdapter extends AIServerAdapter {

    private final ObjectMapper objectMapper;

    public MockAIServerAdapter(ObjectMapper objectMapper, RestClient aiStreamClient,
        QuizOrchestrationService quizOrchestrationService) {
        super(quizOrchestrationService);
        this.objectMapper = objectMapper;
    }

    @Override
    public void streamRequest(GenerationRequestToAI request,
        Consumer<ProblemSetGeneratedEvent> onLineReceived) {
        List<Integer> referencedPages = request.pageNumbers();
        List<Integer> pages = (referencedPages == null || referencedPages.isEmpty())
            ? List.of(1)
            : referencedPages;

        int quizCount = request.quizCount();
        List<Map<String, Object>> quiz = new ArrayList<>();
        for (int i = 1; i <= quizCount / 3; i++) {
            quiz.add(Map.of(
                "number", i,
                "title", "Mock question " + i,
                "selections", List.of(
                    Map.of("content", "Option A", "correct", true),
                    Map.of("content", "Option B", "correct", false),
                    Map.of("content", "Option C", "correct", false),
                    Map.of("content", "Option D", "correct", false)
                ),
                "explanation", "Mock explanation for question " + i,
                "referencedPages", pages
            ));
        }

        ProblemSetGeneratedEvent event = objectMapper.convertValue(
            Map.of(
                "type", "quiz",
                "quiz", quiz),
            ProblemSetGeneratedEvent.class
        );
        onLineReceived.accept(event);
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        referencedPages = request.pageNumbers();
        pages = (referencedPages == null || referencedPages.isEmpty())
            ? List.of(1)
            : referencedPages;

        quiz = new ArrayList<>();
        for (int i = quizCount / 3 + 1; i <= 2 * (quizCount / 3); i++) {
            quiz.add(Map.of(
                "number", i,
                "title", "Mock question " + i,
                "selections", List.of(
                    Map.of("content", "Option A", "correct", true),
                    Map.of("content", "Option B", "correct", false),
                    Map.of("content", "Option C", "correct", false),
                    Map.of("content", "Option D", "correct", false)
                ),
                "explanation", "Mock explanation for question " + i,
                "referencedPages", pages
            ));
        }

        event = objectMapper.convertValue(
            Map.of(
                "type", "quiz",
                "quiz", quiz),
            ProblemSetGeneratedEvent.class
        );
        onLineReceived.accept(event);

        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        referencedPages = request.pageNumbers();
        pages = (referencedPages == null || referencedPages.isEmpty())
            ? List.of(1)
            : referencedPages;

        quiz = new ArrayList<>();
        for (int i = 2 * (quizCount / 3) + 1; i <= quizCount; i++) {
            quiz.add(Map.of(
                "number", i,
                "title", "Mock question " + i,
                "selections", List.of(
                    Map.of("content", "Option A", "correct", true),
                    Map.of("content", "Option B", "correct", false),
                    Map.of("content", "Option C", "correct", false),
                    Map.of("content", "Option D", "correct", false)
                ),
                "explanation", "Mock explanation for question " + i,
                "referencedPages", pages
            ));
        }

        event = objectMapper.convertValue(
            Map.of(
                "type", "quiz",
                "quiz", quiz),
            ProblemSetGeneratedEvent.class
        );
        onLineReceived.accept(event);
    }
}