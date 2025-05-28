package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {
    List<Problem> findByProblemSet(ProblemSet problemSet);
    List<Problem> findByIdProblemSetId(Long problemSetId);

}
