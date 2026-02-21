package com.icc.qasker.auth.service;

import com.icc.qasker.auth.entity.Board;
import com.icc.qasker.auth.entity.BoardStatus;
import com.icc.qasker.auth.entity.Reply;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.BoardRepository;
import com.icc.qasker.auth.repository.ReplyRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ReplyRepository replyRepository;
    private final BoardRepository boardRepository;

    @Transactional
    public void reply(Long boardId, User user, String content) {
        if (user == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
        board.changeStatus(BoardStatus.ANSWERED);
        Reply reply = Reply.builder().board(board).admin(user).content(content).build();
        replyRepository.save(reply);
    }
}
