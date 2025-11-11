package com.icc.qasker.repository;

import com.icc.qasker.entity.Problem;
import com.icc.qasker.entity.Selection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectionRepository extends JpaRepository<Selection, Long> {

    List<Selection> findByProblem(Problem problem);
}
