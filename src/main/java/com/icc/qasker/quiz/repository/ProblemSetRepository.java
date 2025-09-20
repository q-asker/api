package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.ProblemSet;
import com.newrelic.api.agent.Trace;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    @Trace
    Optional<ProblemSet> getProblemSetById(Long id);
}
