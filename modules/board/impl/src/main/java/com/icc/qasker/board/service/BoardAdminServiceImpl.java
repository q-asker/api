package com.icc.qasker.board.service;

import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
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
public class BoardAdminServiceImpl implements BoardAdminService {

  private final BoardRepository boardRepository;
  private final ReplyRepository replyRepository;

  @Override
  @Transactional
  public void createUpdateLog(PostRequest request, String adminUserId) {
    Board board =
        Board.builder()
            .title(request.title())
            .content(request.content())
            .userId(adminUserId)
            .category(BoardCategory.UPDATE_LOG)
            .build();
    boardRepository.save(board);
  }

  @Override
  @Transactional
  public void reply(Long boardId, String adminUserId, String content) {
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
    board.changeStatus(BoardStatus.ANSWERED);
    Reply reply = Reply.builder().board(board).adminId(adminUserId).content(content).build();
    replyRepository.save(reply);
  }

  @Override
  @Transactional
  public void updateUpdateLog(Long boardId, PostRequest request, String adminUserId) {
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
    if (board.getCategory() != BoardCategory.UPDATE_LOG) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    board.update(request.title(), request.content());
  }
}
