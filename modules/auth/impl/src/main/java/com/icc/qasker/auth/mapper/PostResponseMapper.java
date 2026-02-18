package com.icc.qasker.auth.mapper;

import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.entity.Board;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostResponseMapper {

    public PostResponse fromEntity(Board board) {
        return new PostResponse(board.getBoardId(), board.getUser().getNickname(), board.getTitle(),
            board.getViewCount(), board.getStatus().name(), board.getCreatedAt());
    }
}
