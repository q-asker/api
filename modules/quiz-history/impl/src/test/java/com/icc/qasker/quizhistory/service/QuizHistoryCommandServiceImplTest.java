package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.entity.QuizHistory.QuizHistoryStatus;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class QuizHistoryCommandServiceImplTest {

  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private ProblemSetReadService problemSetReadService;
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

  @Test
  @DisplayName("saveHistory: 같은 (user,set) 동시 저장 INSERT 충돌을 재조회로 흡수한다 (500 없이 기존 행 완료)")
  void saveHistory_absorbs_concurrent_insert_race() {
    QuizHistory existing =
        QuizHistory.builder().id(7L).userId("u1").problemSetId(10L).title("t").build();
    when(hashUtil.decode("enc")).thenReturn(10L);
    when(problemSetReadService.findProblemSetById(10L)).thenReturn(Optional.empty());
    // 최초 조회는 없음 → save가 유니크 충돌(다른 트랜잭션이 먼저 INSERT) → 재조회는 기존 행 반환
    when(quizHistoryRepository.findByUserIdAndProblemSetId("u1", 10L))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(quizHistoryRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("dup (user_id, problem_set_id)"))
        .thenReturn(existing);
    when(hashUtil.encode(7L)).thenReturn("encHist");

    SaveHistoryRequest request = new SaveHistoryRequest("enc", "t", List.of(), 3, "00:01:00");

    // 충돌을 던지지 않고(=500 없음) 기존 행으로 흡수해 완료 처리한다.
    String result = service.saveHistory("u1", request);

    assertThat(result).isEqualTo("encHist");
    assertThat(existing.getStatus()).isEqualTo(QuizHistoryStatus.COMPLETED);
    assertThat(existing.getScore()).isEqualTo(3);
  }

  @Test
  @DisplayName("saveHistory: 기존 이력이 있으면 새로 만들지 않고 완료 처리한다")
  void saveHistory_updates_existing() {
    QuizHistory existing =
        QuizHistory.builder().id(7L).userId("u1").problemSetId(10L).title("t").build();
    when(hashUtil.decode("enc")).thenReturn(10L);
    when(problemSetReadService.findProblemSetById(10L)).thenReturn(Optional.empty());
    when(quizHistoryRepository.findByUserIdAndProblemSetId("u1", 10L))
        .thenReturn(Optional.of(existing));
    when(quizHistoryRepository.save(existing)).thenReturn(existing);
    when(hashUtil.encode(7L)).thenReturn("encHist");

    String result =
        service.saveHistory("u1", new SaveHistoryRequest("enc", "t", List.of(), 5, "00:02:00"));

    assertThat(result).isEqualTo("encHist");
    assertThat(existing.getStatus()).isEqualTo(QuizHistoryStatus.COMPLETED);
    assertThat(existing.getScore()).isEqualTo(5);
  }
}
