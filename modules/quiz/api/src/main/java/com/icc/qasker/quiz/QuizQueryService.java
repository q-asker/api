package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quiz.view.QuizView;
import java.util.List;
import java.util.Optional;

public interface QuizQueryService {

    List<QuizView> getQuizViews(long problemSetId, List<Integer> numbers);

    Optional<GenerationStatus> getGenerationStatusBySessionId(String sessionId);

    ProblemSetResponse getMissedProblems(String sessionId, int lastQuizNumber);
}
