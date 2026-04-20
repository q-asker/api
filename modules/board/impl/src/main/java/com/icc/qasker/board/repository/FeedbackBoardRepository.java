package com.icc.qasker.board.repository;

import com.icc.qasker.board.entity.FeedbackBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackBoardRepository extends JpaRepository<FeedbackBoard, Long> {}
