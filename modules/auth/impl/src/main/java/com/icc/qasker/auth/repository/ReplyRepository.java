package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.Board;
import com.icc.qasker.auth.entity.Reply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    List<Reply> findByBoard(Board board);
}
