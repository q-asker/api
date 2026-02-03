package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public interface QuizCommandService {

    @Transactional(readOnly = true)
    GenerationStatus getGenerationStatus(Long problemSetId);

    @Transactional(readOnly = true)
    GenerationStatus getGenerationStatusBySessionId(String sessionId);

    @Transactional(readOnly = true)
    Long getCount(Long id);

    @Transactional(readOnly = true)
    ProblemSetResponse getMissedProblems(String sessionId, String lastEventId);

    @Transactional
    Long initProblemSet(String userId, Integer totalQuizCount, QuizType quizType);

    @Transactional
    void updateStatus(Long problemSetId, String status);

    @Transactional
    List<QuizForFe> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);

    @Transactional
    void delete(Long id);
}
