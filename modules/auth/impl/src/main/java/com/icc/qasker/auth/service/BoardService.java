package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.request.PostRequest;
import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.dto.response.SinglePostResponse;
import com.icc.qasker.auth.entity.Board;
import com.icc.qasker.auth.entity.Reply;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.mapper.PostResponseMapper;
import com.icc.qasker.auth.repository.BoardRepository;
import com.icc.qasker.auth.repository.ReplyRepository;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final ReplyRepository replyRepository;
    private final PostResponseMapper postResponseMapper;

    public Page<PostResponse> getPosts(Pageable pageable) {
        return boardRepository.findAll(pageable).map(postResponseMapper::fromEntity);
    }

    @Transactional
    public SinglePostResponse getPost(Long boardId) {
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

        board.updateViewCount();

        List<String> replies = replyRepository.findByBoard(board).stream()
            .map(Reply::getContent)
            .toList();

        return new SinglePostResponse(
            board.getBoardId(),
            board.getUser().getNickname(),
            board.getTitle(),
            board.getContent(),
            board.getViewCount(),
            board.getStatus().name(),
            board.getCreatedAt(),
            replies
        );
    }

    @Transactional
    public void createPost(PostRequest request) {
        User user = getUser();
        Board board = Board.builder().title(request.title()).content(request.content()).user(user)
            .build();
        boardRepository.save(board);
    }

    @Transactional
    public void updatePost(Long boardId, PostRequest request) {
        User user = getUser();
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
        if (user.getUserId().equals(board.getUser().getUserId())) {
            throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
        }
        board.update(request.title(), request.content());
    }

    private User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        return userRepository.findById(((User) authentication.getPrincipal()).getUserId())
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));
    }
}
