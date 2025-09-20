package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.newrelic.api.agent.Trace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

    @Trace
    List<Problem> findByIdProblemSetId(Long problemSetId);

    @Trace
    Optional<Problem> findByIdProblemSetIdAndIdNumber(Long problemSetId, int number);
}

