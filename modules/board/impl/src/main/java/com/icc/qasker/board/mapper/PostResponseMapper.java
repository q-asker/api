package com.icc.qasker.board.mapper;

import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostResponseMapper {

  public PostResponse fromEntity(Board board, String nickname) {
    return new PostResponse(
        board.getBoardId(),
        nickname,
        board.getTitle(),
        board.getViewCount(),
        board.getStatus().name(),
        board.getCreatedAt());
  }
}
