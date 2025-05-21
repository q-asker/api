package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

}
