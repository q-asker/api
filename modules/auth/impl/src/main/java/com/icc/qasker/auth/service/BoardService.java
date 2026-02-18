package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.request.PostCreateRequest;
import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.entity.Board;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.mapper.PostResponseMapper;
import com.icc.qasker.auth.repository.BoardRepository;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
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
    private final PostResponseMapper postResponseMapper;

    public Page<PostResponse> getPosts(Pageable pageable) {
        return boardRepository.findAll(pageable).map(postResponseMapper::fromEntity);
    }

    @Transactional
    public void createPost(PostCreateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        User user = userRepository.findById(((User) authentication.getPrincipal()).getUserId())
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));

        Board board = Board.builder().title(request.title()).content(request.content()).user(user)
            .build();
        boardRepository.save(board);
    }

}
