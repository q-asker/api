package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long> {

    Page<Board> findAll(Pageable pageable);

    @Modifying
    @Query("update Board b set b.viewCount = b.viewCount + 1 where b.boardId = :boardId")
    int incrementViewCount(@Param("boardId") Long boardId);
}
