package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import java.util.List;

public interface QuizCommandService {

    Long initProblemSet(String userId, String sessionId, Integer totalQuizCount,
        QuizType quizType);

    void updateStatus(Long problemSetId, GenerationStatus status);

    List<QuizForFe> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);
}
