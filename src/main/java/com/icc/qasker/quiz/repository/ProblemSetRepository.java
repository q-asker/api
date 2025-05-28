package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.ProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    ProblemSet getProblemSetById(Long id);
}
