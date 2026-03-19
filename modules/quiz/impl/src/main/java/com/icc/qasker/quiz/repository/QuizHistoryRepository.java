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

  Optional<QuizHistory> findFirstByProblemSetIdAndUserIdAndDeletedFalseOrderByCreatedAtDesc(
      Long problemSetId, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory qh WHERE qh.problemSetId = :problemSetId AND qh.userId = :userId")
  void deleteAllByProblemSetIdAndUserId(
      @Param("problemSetId") Long problemSetId, @Param("userId") String userId);

  @Modifying
  @Query(
      "UPDATE QuizHistory qh SET qh.deleted = true WHERE qh.problemSetId = :problemSetId AND qh.userId = :userId")
  int softDeleteByProblemSetIdAndUserId(
      @Param("problemSetId") Long problemSetId, @Param("userId") String userId);
}
