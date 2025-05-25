package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SelectionRepository extends JpaRepository<Selection,Long> {
    List<Selection> findByProblem(Problem problem);
}
