package com.icc.qasker.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.BoardStatus;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.repository.ReplyRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BoardAdminServiceImplTest {

  @Mock BoardRepository boardRepository;
  @Mock ReplyRepository replyRepository;

  @InjectMocks BoardAdminServiceImpl boardAdminService;

  private Board inquiryBoard() {
    Board board = Board.builder().userId("user1").title("제목").content("내용").build();
    ReflectionTestUtils.setField(board, "boardId", 1L);
    return board;
  }

  private Board updateLogBoard() {
    Board board =
        Board.builder()
            .userId("admin1")
            .title("업데이트")
            .content("내용")
            .category(BoardCategory.UPDATE_LOG)
            .build();
    ReflectionTestUtils.setField(board, "boardId", 2L);
    return board;
  }

  @Nested
  @DisplayName("reply — 답변 등록")
  class ReplyPost {

    @Test
    @DisplayName("답변 등록 시 게시글 status가 ANSWERED로 변경된다")
    void reply_changesStatusToAnswered() {
      Board board = inquiryBoard();
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      boardAdminService.reply(1L, "admin1", "답변내용");

      assertThat(board.getStatus()).isEqualTo(BoardStatus.ANSWERED);
    }

    @Test
    @DisplayName("답변 등록 시 Reply가 저장된다")
    void reply_savesReply() {
      Board board = inquiryBoard();
      when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

      boardAdminService.reply(1L, "admin1", "답변내용");

      verify(replyRepository).save(any(com.icc.qasker.board.entity.Reply.class));
    }
  }

  @Nested
  @DisplayName("updateUpdateLog — 업데이트 로그 수정")
  class UpdateUpdateLog {

    @Test
    @DisplayName("UPDATE_LOG가 아닌 게시글 → NOT_ENOUGH_ACCESS 예외")
    void nonUpdateLog_notEnoughAccess() {
      when(boardRepository.findById(1L)).thenReturn(Optional.of(inquiryBoard()));

      assertThatThrownBy(
              () -> boardAdminService.updateUpdateLog(1L, new PostRequest("제목", "내용"), "admin1"))
          .isInstanceOf(CustomException.class)
          .hasMessageContaining(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
    }

    @Test
    @DisplayName("UPDATE_LOG 게시글 — 제목·내용이 변경된다")
    void success() {
      Board board = updateLogBoard();
      when(boardRepository.findById(2L)).thenReturn(Optional.of(board));

      boardAdminService.updateUpdateLog(2L, new PostRequest("새제목", "새내용"), "admin1");

      assertThat(board.getTitle()).isEqualTo("새제목");
      assertThat(board.getContent()).isEqualTo("새내용");
    }
  }
}
