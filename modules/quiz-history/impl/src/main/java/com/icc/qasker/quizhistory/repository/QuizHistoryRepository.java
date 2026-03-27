package com.icc.qasker.quizhistory.repository;

import com.icc.qasker.quizhistory.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByUserId(String userId);

  Optional<QuizHistory> findByIdAndUserId(Long id, String userId);

  Optional<QuizHistory> findByUserIdAndProblemSetId(String userId, Long problemSetId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.id = :id AND h.userId = :userId")
  void deleteByIdAndUserId(Long id, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.userId = :userId")
  void deleteAllByUserId(String userId);
}
