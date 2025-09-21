package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectionRepository extends JpaRepository<Selection, Long> {

    List<Selection> findByProblem(Problem problem);
}
