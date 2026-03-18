package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  // deleted 여부와 무관하게 조회 (getHistoryList에서 필터링 용도)
  List<QuizHistory> findAllByProblemSetIdInAndUserId(List<Long> problemSetIds, String userId);

  // 삭제되지 않은 최신 히스토리 1건 (getHistoryDetail)
  Optional<QuizHistory> findFirstByProblemSetIdAndUserIdAndDeletedFalseOrderByCreatedAtDesc(
      Long problemSetId, String userId);

  // saveHistory 재저장 시 기존 레코드 전체 삭제
  @Modifying
  @Query("DELETE FROM QuizHistory qh WHERE qh.problemSetId = :problemSetId AND qh.userId = :userId")
  void deleteAllByProblemSetIdAndUserId(
      @Param("problemSetId") Long problemSetId, @Param("userId") String userId);

  // 소프트 딜리트
  @Modifying
  @Query(
      "UPDATE QuizHistory qh SET qh.deleted = true WHERE qh.problemSetId = :problemSetId AND qh.userId = :userId")
  int softDeleteByProblemSetIdAndUserId(
      @Param("problemSetId") Long problemSetId, @Param("userId") String userId);
}
