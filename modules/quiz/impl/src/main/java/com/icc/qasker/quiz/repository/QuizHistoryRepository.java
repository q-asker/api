package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByUserId(String userId);

  Optional<QuizHistory> findFirstByProblemSetIdAndUserIdOrderByCreatedAtDesc(
      Long problemSetId, String userId);

  List<QuizHistory> findAllByProblemSetIdAndUserId(Long problemSetId, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.problemSetId = :problemSetId AND h.userId = :userId")
  void deleteAllByProblemSetIdAndUserId(Long problemSetId, String userId);

  @Modifying
  @Query(
      value =
          "INSERT INTO quiz_history (user_id, problem_set_id, title, answers, score, created_at) "
              + "VALUES (:userId, :problemSetId, :title, '[]', 0, NOW()) "
              + "ON DUPLICATE KEY UPDATE answers = '[]', score = 0, "
              + "total_time = null, created_at = NOW()",
      nativeQuery = true)
  void upsertInitHistory(String userId, Long problemSetId, String title);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.userId = :userId")
  void deleteAllByUserId(String userId);
}
