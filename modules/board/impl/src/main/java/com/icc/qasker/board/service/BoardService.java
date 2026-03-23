package com.icc.qasker.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.auth.UserService;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardEventOutbox;
import com.icc.qasker.board.entity.BoardStatus;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.event.BoardEventPayload;
import com.icc.qasker.board.event.BoardEventType;
import com.icc.qasker.board.mapper.PostResponseMapper;
import com.icc.qasker.board.repository.BoardEventOutboxRepository;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BoardService {

  private final BoardRepository boardRepository;
  private final BoardEventOutboxRepository outboxRepository;
  private final PostResponseMapper postResponseMapper;
  private final UserService userService;
  private final ObjectMapper objectMapper;

  public Page<PostResponse> getPosts(Pageable pageable) {
    Page<Board> boards = boardRepository.findAll(pageable);
    List<String> userIds = boards.stream().map(Board::getUserId).distinct().toList();
    Map<String, String> nicknames = userService.getNickNames(userIds);

    return boards.map(
        board -> {
          String nickname = nicknames.getOrDefault(board.getUserId(), "알 수 없음");
          return postResponseMapper.fromEntity(board, nickname);
        });
  }

  @Transactional
  public PostDetailResponse getPost(Long boardId, String requestUserId) {
    Board board =
        boardRepository
            .findByIdWithReplies(boardId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.POST_NOT_FOUND));

    board.incrementViewCount();

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
        isWriter);
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
    saveOutbox(board.getBoardId(), userId, request.title(), BoardEventType.POST_CREATED);
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

    if (board.getStatus() == BoardStatus.ANSWERED) {
      throw new CustomException(ExceptionMessage.ALREADY_ANSWERED);
    }

    if (!userId.equals(board.getUserId())) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    board.update(request.title(), request.content());
    saveOutbox(boardId, userId, request.title(), BoardEventType.POST_UPDATED);
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

    if (board.getStatus() == BoardStatus.ANSWERED) {
      throw new CustomException(ExceptionMessage.ALREADY_ANSWERED);
    }
    if (!userId.equals(board.getUserId())) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    board.changeStatus(BoardStatus.DELETED);
    saveOutbox(boardId, userId, board.getTitle(), BoardEventType.POST_DELETED);
  }

  private void saveOutbox(Long boardId, String userId, String title, BoardEventType eventType) {
    try {
      BoardEventPayload payload =
          new BoardEventPayload(eventType, boardId, userId, title, Instant.now());
      String json = objectMapper.writeValueAsString(payload);
      outboxRepository.save(
          BoardEventOutbox.builder().boardId(boardId).eventType(eventType).payload(json).build());
    } catch (Exception e) {
      log.error("Outbox 저장 실패 - boardId: {}, eventType: {}", boardId, eventType, e);
    }
  }
}
