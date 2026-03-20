package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.QuizHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

  List<QuizHistory> findAllByUserId(String userId);

  Optional<QuizHistory> findFirstByProblemSetIdAndUserIdOrderByCreatedAtDesc(
      Long problemSetId, String userId);

  List<QuizHistory> findAllByProblemSetIdAndUserId(Long problemSetId, String userId);
}
