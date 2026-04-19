package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.view.QuizView;
import java.util.List;
import java.util.Optional;

public interface QuizQueryService {

  List<QuizView> getQuizViews(long problemSetId, List<Integer> numbers);

  Optional<GenerationStatus> getGenerationStatusBySessionId(String sessionId);

  ProblemSetResponse getMissedProblems(String sessionId, int lastQuizNumber);
}
