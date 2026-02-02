package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class QuizCommandService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;

    @Transactional
    public Long initProblemSet(String userId) {
        ProblemSet problemSet = ProblemSet
            .builder()
            .userId(userId)
            .build();
        problemSetRepository.save(problemSet);
        return problemSet.getId();
    }
}
