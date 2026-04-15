package com.icc.qasker.board.service;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardStatus;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.mapper.PostResponseMapper;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

  private static final String UPDATE_LOG_USERNAME = "운영팀";

  private final BoardRepository boardRepository;
  private final PostResponseMapper postResponseMapper;
  private final UserService userService;

  public PostPageResponse getPosts(BoardCategory category, Pageable pageable) {
    Page<Board> boards = boardRepository.findByCategory(category, pageable);

    if (category == BoardCategory.UPDATE_LOG) {
      List<PostResponse> posts =
          boards.map(board -> postResponseMapper.fromEntity(board, null)).getContent();
      return new PostPageResponse(
          posts,
          boards.getTotalElements(),
          boards.getTotalPages(),
          boards.getSize(),
          boards.getNumber());
    }

    List<String> userIds = boards.stream().map(Board::getUserId).distinct().toList();
    Map<String, String> nicknames = userService.getNickNames(userIds);

    List<PostResponse> posts =
        boards
            .map(
                board -> {
                  String nickname = nicknames.getOrDefault(board.getUserId(), "알 수 없음");
                  return postResponseMapper.fromEntity(board, nickname);
                })
            .getContent();

    return new PostPageResponse(
        posts,
        boards.getTotalElements(),
        boards.getTotalPages(),
        boards.getSize(),
        boards.getNumber());
  }

  @Transactional
  public PostDetailResponse getPost(Long boardId, String requestUserId) {
    Board board =
        boardRepository
            .findByIdWithReplies(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

    board.incrementViewCount();

    if (board.getCategory() == BoardCategory.UPDATE_LOG) {
      return new PostDetailResponse(
          board.getBoardId(),
          UPDATE_LOG_USERNAME,
          board.getTitle(),
          board.getContent(),
          board.getViewCount(),
          null,
          board.getCreatedAt(),
          List.of(),
          false,
          board.getCategory().name());
    }

    List<String> replies = board.getReplies().stream().map(Reply::getContent).toList();
    boolean isWriter = Objects.equals(requestUserId, board.getUserId());

    return new PostDetailResponse(
        board.getBoardId(),
        userService.getUserNickname(board.getUserId()),
        board.getTitle(),
        board.getContent(),
        board.getViewCount(),
        board.getStatus().name(),
        board.getCreatedAt(),
        replies,
        isWriter,
        board.getCategory().name());
  }

  @Transactional
  public void createPost(PostRequest request, String userId) {
    if (userId == null) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }
    userService.checkUserExists(userId);
    Board board =
        Board.builder().title(request.title()).content(request.content()).userId(userId).build();
    boardRepository.save(board);
  }

  @Transactional
  public void updatePost(Long boardId, PostRequest request, String userId) {
    if (userId == null) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }

    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

    if (board.getCategory() == BoardCategory.UPDATE_LOG) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }

    if (board.getStatus() == BoardStatus.ANSWERED) {
      throw new CustomException(ExceptionMessage.ALREADY_ANSWERED);
    }

    if (!userId.equals(board.getUserId())) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    board.update(request.title(), request.content());
  }

  @Transactional
  public void deletePost(Long boardId, String userId) {
    if (userId == null) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

    if (board.getCategory() == BoardCategory.UPDATE_LOG) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }

    if (board.getStatus() == BoardStatus.ANSWERED) {
      throw new CustomException(ExceptionMessage.ALREADY_ANSWERED);
    }
    if (!userId.equals(board.getUserId())) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    board.changeStatus(BoardStatus.DELETED);
  }
}
