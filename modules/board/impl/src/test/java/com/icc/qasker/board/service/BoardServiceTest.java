package com.icc.qasker.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardStatus;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.mapper.PostResponseMapper;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

  @Mock BoardRepository boardRepository;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  PostResponseMapper postResponseMapper;

  @Mock UserService userService;

  @InjectMocks BoardServiceImpl boardService;

  private Board inquiryBoard(String userId) {
    Board board = Board.builder().userId(userId).title("제목").content("내용").build();
    ReflectionTestUtils.setField(board, "boardId", 1L);
    return board;
  }

  private Board updateLogBoard(String userId) {
    Board board =
        Board.builder()
            .userId(userId)
            .title("업데이트")
            .content("내용")
            .category(BoardCategory.UPDATE_LOG)
            .build();
    ReflectionTestUtils.setField(board, "boardId", 2L);
    return board;
  }

  @Nested
  @DisplayName("getPosts — 목록 조회")
  class GetPosts {

    @Test
    @DisplayName("INQUIRY: 닉네임이 올바르게 매핑된다")
    void inquiry_nicknameMapping() {
      Board board = inquiryBoard("user1");
      Pageable pageable = PageRequest.of(0, 10);
      Page<Board> page = new PageImpl<>(List.of(board), pageable, 1);

      when(boardRepository.findByCategory(BoardCategory.INQUIRY, pageable)).thenReturn(page);
      when(userService.getNickNames(List.of("user1"))).thenReturn(Map.of("user1", "닉네임1"));

      PostPageResponse result = boardService.getPosts(BoardCategory.INQUIRY, pageable);

      assertThat(result.posts()).hasSize(1);
      assertThat(result.posts().get(0).userName()).isEqualTo("닉네임1");
    }

    @Test
    @DisplayName("INQUIRY: userId가 없으면 '알 수 없음'으로 대체된다")
    void inquiry_unknownUser() {
      Board board = inquiryBoard("user-ghost");
      Pageable pageable = PageRequest.of(0, 10);
      Page<Board> page = new PageImpl<>(List.of(board), pageable, 1);

      when(boardRepository.findByCategory(BoardCategory.INQUIRY, pageable)).thenReturn(page);
      when(userService.getNickNames(any())).thenReturn(Map.of());

      PostPageResponse result = boardService.getPosts(BoardCategory.INQUIRY, pageable);

      assertThat(result.posts().get(0).userName()).isEqualTo("알 수 없음");
    }

    @Test
    @DisplayName("INQUIRY: 페이징 필드가 정확하다")
    void inquiry_pagingFields() {
      Board board = inquiryBoard("user1");
      Pageable pageable = PageRequest.of(1, 5);
      Page<Board> page = new PageImpl<>(List.of(board), pageable, 6);

      when(boardRepository.findByCategory(BoardCategory.INQUIRY, pageable)).thenReturn(page);
      when(userService.getNickNames(any())).thenReturn(Map.of("user1", "닉1"));

      PostPageResponse result = boardService.getPosts(BoardCategory.INQUIRY, pageable);

      assertThat(result.totalElements()).isEqualTo(6);
      assertThat(result.totalPages()).isEqualTo(2);
      assertThat(result.size()).isEqualTo(5);
      assertThat(result.number()).isEqualTo(1);
    }

    @Test
    @DisplayName("UPDATE_LOG: 작성자가 '운영팀'으로 표기된다")
    void updateLog_operationsTeamUsername() {
      Board board = updateLogBoard("admin1");
      Pageable pageable = PageRequest.of(0, 10);
      Page<Board> page = new PageImpl<>(List.of(board), pageable, 1);

      when(boardRepository.findByCategory(BoardCategory.UPDATE_LOG, pageable)).thenReturn(page);

      PostPageResponse result = boardService.getPosts(BoardCategory.UPDATE_LOG, pageable);

      assertThat(result.posts().get(0).userName()).isEqualTo("운영팀");
    }

    @Test
    @DisplayName("UPDATE_LOG: status가 null이다")
    void updateLog_statusIsNull() {
      Board board = updateLogBoard("admin1");
      Pageable pageable = PageRequest.of(0, 10);
      Page<Board> page = new PageImpl<>(List.of(board), pageable, 1);

      when(boardRepository.findByCategory(BoardCategory.UPDATE_LOG, pageable)).thenReturn(page);

      PostPageResponse result = boardService.getPosts(BoardCategory.UPDATE_LOG, pageable);

      assertThat(result.posts().get(0).status()).isNull();
    }
  }

  @Nested
  @DisplayName("getPost — 상세 조회")
  class GetPost {

    @Test
    @DisplayName("일반 게시글: 닉네임·replies·status·category가 올바르다")
    void inquiry_detail() {
      Board board = inquiryBoard("user1");
      Reply reply = Reply.builder().board(board).adminId("admin1").content("답변내용").build();
      board.getReplies().add(reply);

      when(boardRepository.findByIdWithReplies(1L)).thenReturn(Optional.of(board));
      when(userService.getUserNickname("user1")).thenReturn("닉네임1");

      PostDetailResponse result = boardService.getPost(1L, "user1");

      assertThat(result.username()).isEqualTo("닉네임1");
      assertThat(result.replies()).containsExactly("답변내용");
      assertThat(result.status()).isEqualTo("IN_PROGRESS");
      assertThat(result.category()).isEqualTo("INQUIRY");
    }

    @Test
    @DisplayName("일반 게시글: 요청자가 작성자이면 isWriter=true")
    void inquiry_isWriter_true() {
      Board board = inquiryBoard("user1");
      when(boardRepository.findByIdWithReplies(1L)).thenReturn(Optional.of(board));
      when(userService.getUserNickname(any())).thenReturn("닉네임");

      PostDetailResponse result = boardService.getPost(1L, "user1");

      assertThat(result.isWriter()).isTrue();
    }

    @Test
    @DisplayName("일반 게시글: 요청자가 작성자가 아니면 isWriter=false")
    void inquiry_isWriter_false() {
      Board board = inquiryBoard("user1");
      when(boardRepository.findByIdWithReplies(1L)).thenReturn(Optional.of(board));
      when(userService.getUserNickname(any())).thenReturn("닉네임");

      PostDetailResponse result = boardService.getPost(1L, "other-user");

      assertThat(result.isWriter()).isFalse();
    }

    @Test
    @DisplayName("일반 게시글: 조회 시 viewCount가 1 증가한다")
    void inquiry_viewCountIncrement() {
      Board board = inquiryBoard("user1");
      when(boardRepository.findByIdWithReplies(1L)).thenReturn(Optional.of(board));
      when(userService.getUserNickname(any())).thenReturn("닉네임");

      PostDetailResponse result = boardService.getPost(1L, "user1");

      assertThat(result.viewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("UPDATE_LOG: 작성자가 '운영팀'이고 status=null, replies 빈 리스트")
    void updateLog_detail() {
      Board board = updateLogBoard("admin1");
      when(boardRepository.findByIdWithReplies(2L)).thenReturn(Optional.of(board));

      PostDetailResponse result = boardService.getPost(2L, "any-user");

      assertThat(result.username()).isEqualTo("운영팀");
      assertThat(result.status()).isNull();
      assertThat(result.replies()).isEmpty();
      assertThat(result.isWriter()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 boardId → POST_NOT_FOUND 예외")
    void notFound() {
      when(boardRepository.findByIdWithReplies(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> boardService.getPost(999L, "user1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.POST_NOT_FOUND.getMessage());
    }
  }

  @Nested
  @DisplayName("createPost — 게시글 생성")
  class CreatePost {

    @Test
    @DisplayName("userId=null → UNAUTHORIZED 예외")
    void nullUserId_unauthorized() {
      assertThatThrownBy(() -> boardService.createPost(new PostRequest("제목", "내용"), null))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.UNAUTHORIZED.getMessage());
    }

    @Test
    @DisplayName("정상 저장 — boardRepository.save가 호출된다")
    void success() {
      boardService.createPost(new PostRequest("제목", "내용"), "user1");

      verify(boardRepository).save(any(Board.class));
    }
  }

  @Nested
  @DisplayName("updatePost — 게시글 수정")
  class UpdatePost {

    private final PostRequest updateRequest = new PostRequest("수정제목", "수정내용");

    @Test
    @DisplayName("userId=null → UNAUTHORIZED 예외")
    void nullUserId_unauthorized() {
      assertThatThrownBy(() -> boardService.updatePost(1L, updateRequest, null))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.UNAUTHORIZED.getMessage());
    }

    @Test
    @DisplayName("UPDATE_LOG 게시글 → NOT_ENOUGH_ACCESS 예외")
    void updateLog_notEnoughAccess() {
      when(boardRepository.findById(2L)).thenReturn(Optional.of(updateLogBoard("admin1")));

      assertThatThrownBy(() -> boardService.updatePost(2L, updateRequest, "admin1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
    }

    @Test
    @DisplayName("ANSWERED 상태 게시글 → ALREADY_ANSWERED 예외")
    void answered_alreadyAnswered() {
      Board board = inquiryBoard("user1");
      board.changeStatus(BoardStatus.ANSWERED);
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      assertThatThrownBy(() -> boardService.updatePost(1L, updateRequest, "user1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.ALREADY_ANSWERED.getMessage());
    }

    @Test
    @DisplayName("비소유자 → NOT_ENOUGH_ACCESS 예외")
    void nonOwner_notEnoughAccess() {
      when(boardRepository.findById(1L)).thenReturn(Optional.of(inquiryBoard("user1")));

      assertThatThrownBy(() -> boardService.updatePost(1L, updateRequest, "other-user"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
    }

    @Test
    @DisplayName("정상 수정 — 제목·내용이 변경된다")
    void success() {
      Board board = inquiryBoard("user1");
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      boardService.updatePost(1L, updateRequest, "user1");

      assertThat(board.getTitle()).isEqualTo("수정제목");
      assertThat(board.getContent()).isEqualTo("수정내용");
    }
  }

  @Nested
  @DisplayName("deletePost — 게시글 삭제")
  class DeletePost {

    @Test
    @DisplayName("userId=null → UNAUTHORIZED 예외")
    void nullUserId_unauthorized() {
      assertThatThrownBy(() -> boardService.deletePost(1L, null))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.UNAUTHORIZED.getMessage());
    }

    @Test
    @DisplayName("UPDATE_LOG 게시글 → NOT_ENOUGH_ACCESS 예외")
    void updateLog_notEnoughAccess() {
      when(boardRepository.findById(2L)).thenReturn(Optional.of(updateLogBoard("admin1")));

      assertThatThrownBy(() -> boardService.deletePost(2L, "admin1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
    }

    @Test
    @DisplayName("ANSWERED 상태 게시글 → ALREADY_ANSWERED 예외")
    void answered_alreadyAnswered() {
      Board board = inquiryBoard("user1");
      board.changeStatus(BoardStatus.ANSWERED);
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      assertThatThrownBy(() -> boardService.deletePost(1L, "user1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.ALREADY_ANSWERED.getMessage());
    }

    @Test
    @DisplayName("비소유자 → NOT_ENOUGH_ACCESS 예외")
    void nonOwner_notEnoughAccess() {
      when(boardRepository.findById(1L)).thenReturn(Optional.of(inquiryBoard("user1")));

      assertThatThrownBy(() -> boardService.deletePost(1L, "other-user"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
    }

    @Test
    @DisplayName("정상 삭제 — status가 DELETED로 변경된다 (소프트 삭제)")
    void success_softDelete() {
      Board board = inquiryBoard("user1");
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      boardService.deletePost(1L, "user1");

      assertThat(board.getStatus()).isEqualTo(BoardStatus.DELETED);
    }
  }
}
