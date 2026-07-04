package com.icc.qasker.board.mapper;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.Reply;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostResponseMapper {

  static final String UPDATE_LOG_USERNAME = "운영팀";

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

  public PostDetailResponse toDetail(Board board, String nickname, boolean isWriter) {
    boolean isUpdateLog = board.getCategory() == BoardCategory.UPDATE_LOG;
    List<String> replies =
        isUpdateLog ? List.of() : board.getReplies().stream().map(Reply::getContent).toList();
    return new PostDetailResponse(
        board.getBoardId(),
        isUpdateLog ? UPDATE_LOG_USERNAME : nickname,
        board.getTitle(),
        board.getContent(),
        board.getViewCount(),
        isUpdateLog ? null : board.getStatus().name(),
        board.getCreatedAt(),
        replies,
        !isUpdateLog && isWriter,
        board.getCategory().name());
  }
}
