package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

    List<Problem> findByIdProblemSetId(Long problemSetId);

    Optional<Problem> findByIdProblemSetIdAndIdNumber(Long problemSetId, int number);

    long countByIdProblemSetId(Long id);

    @Query("""
        SELECT p
        FROM Problem p
        WHERE p.id.number > :number
        AND p.id.problemSetId = :problemSetId
        """)
    List<Problem> findMissedProblems(Long problemSetId, Integer number);
}

