package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.AIProblemSet;
import java.util.List;
import java.util.function.Consumer;

public interface QuizOrchestrationService {

    void generateQuiz(
        String fileUrl,
        String strategy,
        int quizCount,
        List<Integer> referencePages,
        Consumer<AIProblemSet> onChunkCompleted);
}
