package com.icc.qasker.board.mapper;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostResponseMapper {

  private static final String UPDATE_LOG_USERNAME = "운영팀";

  public PostResponse fromEntity(Board board, String nickname) {
    boolean isUpdateLog = board.getCategory() == BoardCategory.UPDATE_LOG;
    return new PostResponse(
        board.getBoardId(),
        isUpdateLog ? UPDATE_LOG_USERNAME : nickname,
        board.getTitle(),
        board.getViewCount(),
        isUpdateLog ? null : board.getStatus().name(),
        board.getCreatedAt(),
        board.getCategory().name());
  }
}
