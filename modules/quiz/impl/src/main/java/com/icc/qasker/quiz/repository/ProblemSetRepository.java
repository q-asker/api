package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.entity.ProblemSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    @Query("""
        SELECT p.status
        FROM ProblemSet p
        WHERE p.sessionId = :sessionId
        ORDER BY p.createdAt DESC""")
    Optional<GenerationStatus> findStatusBySessionId(@Param("sessionId") String sessionId);

    Optional<ProblemSet> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
