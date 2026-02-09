package com.icc.qasker.quiz.service.generation;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
@Transactional
public class QuizCommandServiceImpl implements QuizCommandService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;
    private final ProblemSetResponseMapper problemSetResponseMapper;


    @Override
    public Long initProblemSet(String userId, String sessionId, Integer totalQuizCount,
        QuizType quizType) {
        ProblemSet problemSet = ProblemSet
            .builder()
            .sessionId(sessionId)
            .userId(userId)
            .totalQuizCount(totalQuizCount)
            .quizType(quizType)
            .build();
        ProblemSet saved = problemSetRepository.save(problemSet);
        return saved.getId();
    }

    @Override
    public void updateStatus(Long problemSetId, GenerationStatus status) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        problemSet.updateStatus(status);
    }

    @Override
    public List<QuizForFe> saveBatch(
        List<QuizGeneratedFromAI> generatedProblems,
        Long problemSetId
    ) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

        List<Problem> problems = generatedProblems.stream()
            .map(quiz -> Problem.of(quiz, problemSet))
            .toList();

        List<Problem> saved = problemRepository.saveAll(problems);

        return saved
            .stream()
            .map(problemSetResponseMapper::fromEntity)
            .toList();
    }
}
