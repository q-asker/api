package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.dto.feresponse.FolderListResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.repository.FolderCount;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizFolderQueryServiceImplTest {

  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private HashUtil hashUtil;

  @InjectMocks private QuizFolderQueryServiceImpl service;

  private record Count(Long folderId, long count) implements FolderCount {
    @Override
    public Long getFolderId() {
      return folderId;
    }

    @Override
    public long getCount() {
      return count;
    }
  }

  @Test
  @DisplayName("getFolders: 폴더별 count 매핑(집계 없는 폴더는 0) + unclassifiedCount 반환")
  void getFolders_mapsCountsAndUnclassified() {
    when(quizFolderRepository.findAllByUserIdOrderByCreatedAtDesc("u1"))
        .thenReturn(
            List.of(
                QuizFolder.builder().id(1L).userId("u1").name("수학").build(),
                QuizFolder.builder().id(2L).userId("u1").name("영어").build()));
    when(quizHistoryRepository.countGroupedByFolder("u1"))
        .thenReturn(List.of(new Count(1L, 12L))); // 2번 폴더는 집계 없음 → 0
    when(quizHistoryRepository.countByUserIdAndFolderIdIsNull("u1")).thenReturn(5L);
    lenient().when(hashUtil.encode(anyLong())).thenReturn("ENC");

    FolderListResponse response = service.getFolders("u1");

    assertThat(response.folders()).hasSize(2);
    assertThat(response.folders().get(0).count()).isEqualTo(12L);
    assertThat(response.folders().get(1).count()).isEqualTo(0L);
    assertThat(response.unclassifiedCount()).isEqualTo(5L);
  }
}
