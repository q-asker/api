package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizHistoryCommandServiceImplTest {

  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private HashUtil hashUtil;

  @InjectMocks private QuizHistoryCommandServiceImpl service;

  @Test
  @DisplayName("assignFolder: 소유 기록을 소유 폴더에 배정 (단일 소속 덮어쓰기)")
  void assignFolder_assigns() {
    QuizHistory history =
        QuizHistory.builder().id(1L).userId("u1").problemSetId(10L).folderId(2L).build();
    when(hashUtil.decode("h1")).thenReturn(1L);
    when(quizHistoryRepository.findByIdAndUserId(1L, "u1")).thenReturn(Optional.of(history));
    when(hashUtil.decode("f5")).thenReturn(5L);
    when(quizFolderRepository.findByIdAndUserId(5L, "u1"))
        .thenReturn(Optional.of(QuizFolder.builder().id(5L).userId("u1").name("수학").build()));

    service.assignFolder("u1", "h1", "f5");

    assertThat(history.getFolderId()).isEqualTo(5L);
  }

  @Test
  @DisplayName("assignFolder: folderId=null이면 미분류로 해제")
  void assignFolder_null_clears() {
    QuizHistory history =
        QuizHistory.builder().id(1L).userId("u1").problemSetId(10L).folderId(2L).build();
    when(hashUtil.decode("h1")).thenReturn(1L);
    when(quizHistoryRepository.findByIdAndUserId(1L, "u1")).thenReturn(Optional.of(history));

    service.assignFolder("u1", "h1", null);

    assertThat(history.getFolderId()).isNull();
  }

  @Test
  @DisplayName("assignFolder: 타인/미존재 기록은 QUIZ_HISTORY_NOT_FOUND")
  void assignFolder_historyNotFound() {
    when(hashUtil.decode("h1")).thenReturn(1L);
    when(quizHistoryRepository.findByIdAndUserId(1L, "u1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assignFolder("u1", "h1", "f5"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("assignFolder: 타인/미존재 폴더는 FOLDER_NOT_FOUND")
  void assignFolder_folderNotFound() {
    QuizHistory history = QuizHistory.builder().id(1L).userId("u1").problemSetId(10L).build();
    when(hashUtil.decode("h1")).thenReturn(1L);
    when(quizHistoryRepository.findByIdAndUserId(1L, "u1")).thenReturn(Optional.of(history));
    when(hashUtil.decode("f5")).thenReturn(5L);
    when(quizFolderRepository.findByIdAndUserId(5L, "u1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assignFolder("u1", "h1", "f5"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.FOLDER_NOT_FOUND.getMessage());
  }
}
