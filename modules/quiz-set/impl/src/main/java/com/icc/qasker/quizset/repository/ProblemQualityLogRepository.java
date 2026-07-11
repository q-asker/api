package com.icc.qasker.quizset.repository;

import com.icc.qasker.quizset.entity.ProblemQualityLog;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemQualityLogRepository extends JpaRepository<ProblemQualityLog, Long> {

  Optional<ProblemQualityLog> findByProblemSetIdAndNumber(Long problemSetId, int number);

  // 해설 검증(explanation-review)용 — Pass-2 지연 그룹(질문 JSON·피드백)은 미조회(지연 유지).
  List<ProblemQualityLog> findByProblemSetIdIn(Collection<Long> problemSetIds);

  // Pass-2 재검토(quality-review)용 — 질문 JSON·피드백(pass2 그룹)을 한 쿼리로 eager 로드해 행별 지연 로드(N+1)를 없앤다.
  @EntityGraph(attributePaths = {"v1QuestionJson", "v2QuestionJson", "v1Feedback", "v2Feedback"})
  List<ProblemQualityLog> findWithPass2ByProblemSetIdIn(Collection<Long> problemSetIds);
}
