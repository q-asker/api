package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
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
class QuizFolderCommandServiceImplTest {

  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private HashUtil hashUtil;

  @InjectMocks private QuizFolderCommandServiceImpl service;

  @Test
  @DisplayName("createFolder: 상한 미만이면 이름을 trim해 저장하고 인코딩 id를 반환")
  void createFolder_success() {
    when(quizFolderRepository.countByUserId("u1")).thenReturn(3L);
    when(quizFolderRepository.save(any()))
        .thenReturn(QuizFolder.builder().id(1L).userId("u1").name("수학").build());
    when(hashUtil.encode(anyLong())).thenReturn("ENC");

    FolderResponse response = service.createFolder("u1", "  수학  ");

    assertThat(response.folderId()).isEqualTo("ENC");
    assertThat(response.name()).isEqualTo("수학");
  }

  @Test
  @DisplayName("createFolder: 폴더 100개면 FOLDER_LIMIT_EXCEEDED, 저장하지 않음")
  void createFolder_atLimit_throws() {
    when(quizFolderRepository.countByUserId("u1")).thenReturn(100L);

    assertThatThrownBy(() -> service.createFolder("u1", "새폴더"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.FOLDER_LIMIT_EXCEEDED.getMessage());
    verify(quizFolderRepository, never()).save(any());
  }

  @Test
  @DisplayName("createFolder: 빈/공백/50자 초과 이름은 FOLDER_NAME_INVALID")
  void createFolder_invalidName_throws() {
    assertThatThrownBy(() -> service.createFolder("u1", "   "))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.FOLDER_NAME_INVALID.getMessage());

    String tooLong = "a".repeat(51);
    assertThatThrownBy(() -> service.createFolder("u1", tooLong))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.FOLDER_NAME_INVALID.getMessage());

    verify(quizFolderRepository, never()).countByUserId(any());
    verify(quizFolderRepository, never()).save(any());
  }

  @Test
  @DisplayName("createFolder: 정확히 50자는 허용")
  void createFolder_exactly50_ok() {
    String name = "a".repeat(50);
    when(quizFolderRepository.countByUserId("u1")).thenReturn(0L);
    when(quizFolderRepository.save(any()))
        .thenReturn(QuizFolder.builder().id(1L).userId("u1").name(name).build());
    lenient().when(hashUtil.encode(anyLong())).thenReturn("ENC");

    FolderResponse response = service.createFolder("u1", name);

    assertThat(response.name()).isEqualTo(name);
  }

  @Test
  @DisplayName("renameFolder: 소유 폴더면 rename 호출")
  void renameFolder_success() {
    QuizFolder folder = QuizFolder.builder().id(7L).userId("u1").name("old").build();
    when(hashUtil.decode("f7")).thenReturn(7L);
    when(quizFolderRepository.findByIdAndUserId(7L, "u1")).thenReturn(Optional.of(folder));

    service.renameFolder("u1", "f7", "  new  ");

    assertThat(folder.getName()).isEqualTo("new");
  }

  @Test
  @DisplayName("renameFolder: 타인/미존재 폴더는 FOLDER_NOT_FOUND")
  void renameFolder_notOwned_throws() {
    when(hashUtil.decode("f7")).thenReturn(7L);
    when(quizFolderRepository.findByIdAndUserId(7L, "u1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.renameFolder("u1", "f7", "new"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.FOLDER_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("deleteFolder: 소속 기록을 미분류로 이동 후 폴더 삭제")
  void deleteFolder_movesToUnclassifiedThenDeletes() {
    QuizFolder folder = QuizFolder.builder().id(9L).userId("u1").name("x").build();
    when(hashUtil.decode("f9")).thenReturn(9L);
    when(quizFolderRepository.findByIdAndUserId(9L, "u1")).thenReturn(Optional.of(folder));

    service.deleteFolder("u1", "f9");

    verify(quizHistoryRepository).clearFolderByFolderIdAndUserId(9L, "u1");
    verify(quizFolderRepository).delete(folder);
  }

  @Test
  @DisplayName("deleteFolder: 타인/미존재 폴더는 FOLDER_NOT_FOUND, 이동/삭제 없음")
  void deleteFolder_notOwned_throws() {
    when(hashUtil.decode("f9")).thenReturn(9L);
    when(quizFolderRepository.findByIdAndUserId(9L, "u1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteFolder("u1", "f9")).isInstanceOf(CustomException.class);
    verify(quizHistoryRepository, never()).clearFolderByFolderIdAndUserId(anyLong(), any());
    verify(quizFolderRepository, never()).delete(any());
  }
}
