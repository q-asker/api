package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import java.util.Optional;

public interface QuizQueryService {

    Long getCount(Long id);

    Optional<GenerationStatus> getGenerationStatusBySessionId(String sessionId);

    ProblemSetResponse getMissedProblems(String sessionId, Integer lastQuizNumber);
}
