package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProblemSetService {

    private final ProblemSetRepository problemSetRepository;

    public ProblemSetResponse getProblemSet(Long problemSetId) {
        ProblemSet problemSet = problemSetRepository.getProblemSetById(problemSetId);
        return ProblemSetResponse.of(problemSet);
    }
}
