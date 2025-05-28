package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
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
        ProblemSet problemSet = problemSetRepository.getProblemSetById(problemSetId).orElseThrow(
            () -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND)
        );
        return ProblemSetResponse.of(problemSet);
    }
}
