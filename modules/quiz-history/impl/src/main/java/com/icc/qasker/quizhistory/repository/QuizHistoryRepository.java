package com.icc.qasker.quizhistory.repository;

import com.icc.qasker.quizhistory.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByUserIdOrderByCreatedAtDesc(String userId);

  Page<QuizHistory> findAllByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  Page<QuizHistory> findAllByUserIdAndFolderIdOrderByCreatedAtDesc(
      String userId, Long folderId, Pageable pageable);

  Page<QuizHistory> findAllByUserIdAndFolderIdIsNullOrderByCreatedAtDesc(
      String userId, Pageable pageable);

  long countByUserIdAndFolderIdIsNull(String userId);

  Optional<QuizHistory> findByIdAndUserId(Long id, String userId);

  Optional<QuizHistory> findByUserIdAndProblemSetId(String userId, Long problemSetId);

  /** 사용자의 폴더별 기록 수(미분류 제외). */
  @Query(
      "SELECT h.folderId AS folderId, COUNT(h) AS count FROM QuizHistory h"
          + " WHERE h.userId = :userId AND h.folderId IS NOT NULL GROUP BY h.folderId")
  List<FolderCount> countGroupedByFolder(String userId);

  /** 폴더 삭제 시 소속 기록을 미분류로 되돌린다. */
  @Modifying
  @Query(
      "UPDATE QuizHistory h SET h.folderId = NULL WHERE h.folderId = :folderId AND h.userId = :userId")
  void clearFolderByFolderIdAndUserId(Long folderId, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.id = :id AND h.userId = :userId")
  void deleteByIdAndUserId(Long id, String userId);

  @Modifying
  @Query("DELETE FROM QuizHistory h WHERE h.userId = :userId")
  void deleteAllByUserId(String userId);
}
