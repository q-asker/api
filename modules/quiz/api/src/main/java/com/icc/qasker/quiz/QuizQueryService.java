package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import java.util.Optional;

public interface QuizQueryService {


    Optional<GenerationStatus> getGenerationStatusBySessionId(String sessionId);

    ProblemSetResponse getMissedProblems(String sessionId, int lastQuizNumber);
}
