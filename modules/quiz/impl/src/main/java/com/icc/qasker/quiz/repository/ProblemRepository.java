package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

    @Query("""
        SELECT p
        FROM Problem p
        WHERE p.id.number > :number
        AND p.id.problemSetId = :problemSetId
        ORDER BY p.id.number ASC
        """)
    List<Problem> findRemainingProblems(
        @Param("problemSetId") Long problemSetId,
        @Param("number") Integer number);

    List<Problem> findByIdProblemSetId(Long problemSetId);

    List<Problem> findByIdInOrderByIdNumberAsc(Collection<ProblemId> id);
}
