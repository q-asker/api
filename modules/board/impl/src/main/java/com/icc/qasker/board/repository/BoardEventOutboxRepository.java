package com.icc.qasker.board.repository;

import com.icc.qasker.board.entity.BoardEventOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardEventOutboxRepository extends JpaRepository<BoardEventOutbox, Long> {

  List<BoardEventOutbox> findAllByPublishedFalse();
}
