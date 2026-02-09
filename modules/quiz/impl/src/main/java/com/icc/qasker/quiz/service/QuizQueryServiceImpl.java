package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizQueryService;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
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
@Transactional(readOnly = true)
public class QuizQueryServiceImpl implements QuizQueryService {

    private final HashUtil hashUtil;
    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;
    private final ProblemSetResponseMapper problemSetResponseMapper;


    @Override
    public Long getCount(Long id) {
        return problemRepository.countByIdProblemSetId(id);
    }

    @Override
    public GenerationStatus getGenerationStatusBySessionId(String sessionId) {
        return problemSetRepository
            .findStatusBySessionId(sessionId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    }

    @Override
    public ProblemSetResponse getMissedProblems(String sessionId, Integer lastQuizNumber) {
        ProblemSet problemSet = problemSetRepository
            .findFirstBySessionIdOrderByCreatedAtDesc(sessionId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

        Long problemSetId = problemSet.getId();

        List<Problem> problems = problemRepository.findMissedProblems(problemSetId, lastQuizNumber);
        List<QuizForFe> quizForFeList = problems.stream()
            .map(problemSetResponseMapper::fromEntity).toList();

        return new ProblemSetResponse(
            sessionId,
            hashUtil.encode(problemSetId),
            problemSet.getStatus(),
            problemSet.getQuizType(),
            problemSet.getTotalQuizCount(),
            quizForFeList
        );
    }
}
