package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.Board;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<Board> findAll(Pageable pageable);

    @Query("SELECT b FROM Board b JOIN FETCH b.user LEFT JOIN FETCH b.replies WHERE b.boardId = :boardId")
    Optional<Board> findByIdWithUserAndReplies(@Param("boardId") Long boardId);

    @Modifying
    @Query("update Board b set b.viewCount = b.viewCount + 1 where b.boardId = :boardId")
    int incrementViewCount(@Param("boardId") Long boardId);
}
