package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.Selection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectionRepository extends JpaRepository<Selection,Long> {
}
