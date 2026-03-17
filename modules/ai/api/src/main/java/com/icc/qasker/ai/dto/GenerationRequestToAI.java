package com.icc.qasker.ai.dto;

import java.util.List;
import java.util.function.Consumer;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String fileUrl,
    String strategyValue,
    int quizCount,
    List<Integer> referencePages,
    Consumer<AIProblemSet> questionsConsumer,
    Consumer<List<AIExplanationUpdate>> explanationsConsumer,
    Consumer<Exception> errorConsumer) {}
