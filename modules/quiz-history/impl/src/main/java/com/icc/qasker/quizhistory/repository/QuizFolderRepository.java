package com.icc.qasker.quizhistory.repository;

import com.icc.qasker.quizhistory.entity.QuizFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizFolderRepository extends JpaRepository<QuizFolder, Long> {

  long countByUserId(String userId);

  Optional<QuizFolder> findByIdAndUserId(Long id, String userId);

  List<QuizFolder> findAllByUserIdOrderByCreatedAtDesc(String userId);
}
