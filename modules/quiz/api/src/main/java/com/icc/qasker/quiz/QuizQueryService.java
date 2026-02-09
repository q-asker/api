package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;

public interface QuizQueryService {

    Long getCount(Long id);

    GenerationStatus getGenerationStatusBySessionId(String sessionId);

    ProblemSetResponse getMissedProblems(String sessionId, Integer lastQuizNumber);
}
