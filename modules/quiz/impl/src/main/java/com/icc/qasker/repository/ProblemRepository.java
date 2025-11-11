package com.icc.qasker.repository;

import com.icc.qasker.entity.Problem;
import com.icc.qasker.entity.ProblemId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

    List<Problem> findByIdProblemSetId(Long problemSetId);

    Optional<Problem> findByIdProblemSetIdAndIdNumber(Long problemSetId, int number);
}

