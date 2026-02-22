package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.request.PostRequest;
import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.dto.response.SinglePostResponse;
import com.icc.qasker.auth.entity.Board;
import com.icc.qasker.auth.entity.BoardStatus;
import com.icc.qasker.auth.entity.Reply;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.mapper.PostResponseMapper;
import com.icc.qasker.auth.repository.BoardRepository;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PostResponseMapper postResponseMapper;

    public Page<PostResponse> getPosts(Pageable pageable) {
        return boardRepository.findAll(pageable).map(postResponseMapper::fromEntity);
    }

    @Transactional
    public SinglePostResponse getPost(Long boardId, User requestUser) {
        boardRepository.incrementViewCount(boardId);

        Board board = boardRepository.findByIdWithUserAndReplies(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

        User writeUser = board.getUser();

        List<String> replies = board.getReplies().stream()
            .map(Reply::getContent)
            .toList();

        boolean isWriter = false;
        if (requestUser != null) {
            isWriter = requestUser.getUserId().equals(writeUser.getUserId());
        }

        return new SinglePostResponse(
            board.getBoardId(),
            writeUser.getNickname(),
            board.getTitle(),
            board.getContent(),
            board.getViewCount(),
            board.getStatus().name(),
            board.getCreatedAt(),
            replies,
            isWriter
        );
    }

    @Transactional
    public void createPost(PostRequest request, User user) {
        if (user == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }

        User originUser = userRepository.findById(user.getUserId())
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));

        Board board = Board.builder()
            .title(request.title())
            .content(request.content())
            .user(originUser)
            .build();
        boardRepository.save(board);
    }

    @Transactional
    public void updatePost(Long boardId, PostRequest request, User user) {
        if (user == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }

        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

        if (board.getStatus() == BoardStatus.ANSWERED) {
            throw new CustomException(ExceptionMessage.NOT_ALLOWED_DELETE);
        }

        if (!user.getUserId().equals(board.getUser().getUserId())) {
            throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
        }
        board.update(request.title(), request.content());
    }

    @Transactional
    public void deletePost(Long boardId, User user) {
        if (user == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
        if (board.getStatus() == BoardStatus.ANSWERED) {
            throw new CustomException(ExceptionMessage.NOT_ALLOWED_DELETE);
        }
        board.changeStatus(BoardStatus.DELETED);
    }
}