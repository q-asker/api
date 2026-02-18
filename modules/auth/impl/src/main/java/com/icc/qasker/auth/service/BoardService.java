package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.mapper.PostResponseMapper;
import com.icc.qasker.auth.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final PostResponseMapper postResponseMapper;

    public Page<PostResponse> getPosts(Pageable pageable) {
        return boardRepository.findAll(pageable).map(postResponseMapper::fromEntity);
    }

}
