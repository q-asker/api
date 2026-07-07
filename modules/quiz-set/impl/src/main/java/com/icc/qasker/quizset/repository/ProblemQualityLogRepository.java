package com.icc.qasker.quizset.repository;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemQualityLogRepository extends JpaRepository<ProblemQualityLog, Long> {

  Optional<ProblemQualityLog> findByProblemSetIdAndNumber(Long problemSetId, int number);

  // Pass 2 재검토용 — 세트/묶음 전량 managed 로드(rationale 포함).
  List<ProblemQualityLog> findByProblemSetIdIn(Collection<Long> problemSetIds);
}
