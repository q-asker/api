package com.icc.qasker.quizhistory.repository;

import com.icc.qasker.quizhistory.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByUserId(String userId);

  @Query(
      "SELECT h FROM QuizHistory h WHERE h.problemSetId = :problemSetId AND h.userId = :userId"
          + " ORDER BY h.createdAt DESC LIMIT 1")
  Optional<QuizHistory> findLatestByProblemSetAndUser(Long problemSetId, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.problemSetId = :problemSetId AND h.userId = :userId")
  void deleteAllByProblemSetIdAndUserId(Long problemSetId, String userId);

  @Modifying
  @Query(
      value =
          "INSERT INTO quiz_history (user_id, problem_set_id, title, answers, score, status, created_at) "
              + "VALUES (:userId, :problemSetId, :title, '[]', 0,'NOT_COMPLETED', NOW()) "
              + "ON DUPLICATE KEY UPDATE answers = '[]', score = 0, "
              + "total_time = null, created_at = NOW()",
      nativeQuery = true)
  void upsertInitHistory(String userId, Long problemSetId, String title);

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
  void upsertSaveHistory(String userId, Long problemSetId, String answersJson, Integer score);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.userId = :userId")
  void deleteAllByUserId(String userId);
}
