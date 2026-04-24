package com.icc.qasker.quizhistory.repository;

import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EssayGradeLogRepository extends JpaRepository<EssayGradeLog, Long> {

  /** 각 문제별 가장 최신 채점 로그를 조회한다. */
  @Query(
      """
      SELECT e FROM EssayGradeLog e
      WHERE e.userId = :userId AND e.problemSetId = :problemSetId
        AND e.createdAt = (
          SELECT MAX(e2.createdAt) FROM EssayGradeLog e2
          WHERE e2.userId = e.userId
            AND e2.problemSetId = e.problemSetId
            AND e2.problemNumber = e.problemNumber
        )
      """)
  List<EssayGradeLog> findLatestByUserIdAndProblemSetId(String userId, Long problemSetId);
}
