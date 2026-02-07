package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import java.util.List;

public interface QuizCommandService {

    GenerationStatus getGenerationStatus(Long problemSetId);

    GenerationStatus getGenerationStatusBySessionId(String sessionId);

    Long getCount(Long id);

    ProblemSetResponse getMissedProblems(String sessionId, Integer lastQuizNumber);

    Long initProblemSet(String userId, String sessionId, Integer totalQuizCount,
        QuizType quizType);

    void updateStatus(Long problemSetId, GenerationStatus status);

    List<QuizForFe> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);

    void delete(Long id);
}
