package com.icc.qasker.quizset.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.TestEntityFactory;
import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemSetServiceImplTest {

  @Mock private ProblemSetResponseMapper problemSetResponseMapper;
  @Mock private ProblemSetRepository problemSetRepository;
  @Mock private HashUtil hashUtil;

  private ProblemSetServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ProblemSetServiceImpl(problemSetResponseMapper, problemSetRepository, hashUtil);
  }

  private ProblemSet setOwnedBy(String userId) {
    return TestEntityFactory.problemSet(
        1L, "sess", "old", GenerationStatus.COMPLETED, QuizType.MULTIPLE, 1, userId, List.of());
  }

  @Test
  @DisplayName("소유자가 일치하면 제목을 변경한다")
  void changes_title_when_owner_matches() {
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setOwnedBy("user-1")));

    ChangeTitleResponse response =
        service.changeProblemSetTitle("user-1", "enc", new ChangeTitleRequest("new"));

    assertThat(response.title()).isEqualTo("new");
  }

  @Test
  @DisplayName("소유자가 다르면 NOT_ENOUGH_ACCESS")
  void throws_when_owner_mismatch() {
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setOwnedBy("owner")));

    assertThatThrownBy(
            () -> service.changeProblemSetTitle("intruder", "enc", new ChangeTitleRequest("x")))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
  }

  @Test
  @DisplayName("userId가 null이면 NOT_ENOUGH_ACCESS")
  void throws_when_set_user_null() {
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setOwnedBy(null)));

    assertThatThrownBy(
            () -> service.changeProblemSetTitle("user-1", "enc", new ChangeTitleRequest("x")))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage());
  }
}
