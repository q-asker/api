package com.icc.qasker.board.service;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardStatus;
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
public class BoardServiceImpl implements BoardService {

  private static final String UNKNOWN_USERNAME = "알 수 없음";

  private final BoardRepository boardRepository;
  private final PostResponseMapper postResponseMapper;
  private final UserService userService;

  @Override
  public PostPageResponse getPosts(BoardCategory category, Pageable pageable) {
    Page<Board> boards = boardRepository.findByCategory(category, pageable);

    if (category == BoardCategory.UPDATE_LOG) {
      List<PostResponse> posts =
          boards.map(board -> postResponseMapper.fromEntity(board, null)).getContent();
      return toPageResponse(boards, posts);
    }

    List<String> userIds = boards.stream().map(Board::getUserId).distinct().toList();
    Map<String, String> nicknames = userService.getNickNames(userIds);

    List<PostResponse> posts =
        boards
            .map(
                board ->
                    postResponseMapper.fromEntity(
                        board, nicknames.getOrDefault(board.getUserId(), UNKNOWN_USERNAME)))
            .getContent();

    return toPageResponse(boards, posts);
  }

  @Override
  @Transactional
  public PostDetailResponse getPost(Long boardId, String requestUserId) {
    Board board =
        boardRepository
            .findByIdWithReplies(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

    board.incrementViewCount();

    boolean isUpdateLog = board.getCategory() == BoardCategory.UPDATE_LOG;
    String nickname = isUpdateLog ? null : userService.getUserNickname(board.getUserId());
    boolean isWriter = Objects.equals(requestUserId, board.getUserId());
    return postResponseMapper.toDetail(board, nickname, isWriter);
  }

  @Override
  @Transactional
  public void createPost(PostRequest request, String userId) {
    requireUserId(userId);
    userService.checkUserExists(userId);
    Board board =
        Board.builder().title(request.title()).content(request.content()).userId(userId).build();
    boardRepository.save(board);
  }

  @Override
  @Transactional
  public void updatePost(Long boardId, PostRequest request, String userId) {
    requireUserId(userId);
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
    board.ensureModifiableBy(userId);
    board.update(request.title(), request.content());
  }

  @Override
  @Transactional
  public void deletePost(Long boardId, String userId) {
    requireUserId(userId);
    Board board =
        boardRepository
            .findById(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));
    board.ensureModifiableBy(userId);
    board.changeStatus(BoardStatus.DELETED);
  }

  private void requireUserId(String userId) {
    if (userId == null) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }
  }

  private PostPageResponse toPageResponse(Page<Board> boards, List<PostResponse> posts) {
    return new PostPageResponse(
        posts,
        boards.getTotalElements(),
        boards.getTotalPages(),
        boards.getSize(),
        boards.getNumber());
  }
}
