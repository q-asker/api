package com.icc.qasker.quizset.repository;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.entity.ProblemSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

  @Query(
      """
      SELECT p.generationStatus
      FROM ProblemSet p
      WHERE p.sessionId = :sessionId
      ORDER BY p.createdAt DESC
      LIMIT 1""")
  Optional<GenerationStatus> findGenerationStatusBySessionId(@Param("sessionId") String sessionId);

  Optional<ProblemSet> findFirstBySessionIdOrderByCreatedAtDesc(String sessionId);

  List<ProblemSet> findByGenerationStatusInAndCreatedAtBefore(
      List<GenerationStatus> statuses, Instant threshold);
}
