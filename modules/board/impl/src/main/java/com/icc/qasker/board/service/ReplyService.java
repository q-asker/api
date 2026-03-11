package com.icc.qasker.board.service;

import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardStatus;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.repository.ReplyRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplyService {

  private final ReplyRepository replyRepository;
  private final BoardRepository boardRepository;

  @Transactional
  public void reply(Long boardId, String userId, String content) {
    if (userId == null) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
    board.changeStatus(BoardStatus.ANSWERED);
    Reply reply = Reply.builder().board(board).adminId(userId).content(content).build();
    replyRepository.save(reply);
  }
}
