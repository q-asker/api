package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByProblemSetIdInAndUserId(List<Long> problemSetIds, String userId);

  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO quiz_history (user_id, problem_set_id, answers, score, status, created_at)"
              + " VALUES (:userId, :problemSetId, :answersJson, :score, 'COMPLETED', NOW())"
              + " ON DUPLICATE KEY UPDATE"
              + " answers = VALUES(answers),"
              + " score = VALUES(score),"
              + " status = 'COMPLETED',"
              + " created_at = NOW()",
      nativeQuery = true)
  void upsert(
      @Param("userId") String userId,
      @Param("problemSetId") Long problemSetId,
      @Param("answersJson") String answersJson,
      @Param("score") Integer score);

  Optional<QuizHistory> findByProblemSetIdAndUserId(Long problemSetId, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory qh WHERE qh.problemSetId = :problemSetId AND qh.userId = :userId")
  int deleteByProblemSetIdAndUserId(
      @Param("problemSetId") Long problemSetId, @Param("userId") String userId);
}
