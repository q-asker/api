package com.icc.qasker.ai.dto;

import java.util.List;
import java.util.function.Consumer;

public record GenerationRequestToAI(
    String fileUrl,
    String strategyValue,
    int quizCount,
    List<Integer> referencePages,
    Consumer<AIProblemSet> onChunkCompleted
) {

}
