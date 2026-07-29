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
import com.icc.qasker.quizset.dto.feresponse.RegenerationConditionResponse;
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

  @Test
  @DisplayName("조건이 온전한 세트는 저장 조건을 그대로 되돌려주고 documentAvailable=true (effective quizType 유지)")
  void returns_regeneration_condition_for_complete_set() {
    ProblemSet set =
        ProblemSet.builder()
            .id(1L)
            .sessionId("sess")
            .title("이산수학 3장")
            .generationStatus(GenerationStatus.COMPLETED)
            .quizType(QuizType.REAL_BLANK)
            .totalQuizCount(10)
            .userId("user-1")
            .fileUrl("https://cdn.example/doc.pdf")
            .customInstruction("난이도 높게")
            .pageNumbers(List.of(1, 2, 3))
            .language("KO")
            .build();
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(set));

    RegenerationConditionResponse response = service.getRegenerationCondition("enc");

    assertThat(response.quizType()).isEqualTo(QuizType.REAL_BLANK);
    assertThat(response.quizCount()).isEqualTo(10);
    assertThat(response.pageNumbers()).containsExactly(1, 2, 3);
    assertThat(response.language()).isEqualTo("KO");
    assertThat(response.customInstruction()).isEqualTo("난이도 높게");
    assertThat(response.uploadedUrl()).isEqualTo("https://cdn.example/doc.pdf");
    assertThat(response.title()).isEqualTo("이산수학 3장");
    assertThat(response.documentAvailable()).isTrue();
  }

  @Test
  @DisplayName("legacy 세트(pageNumbers 빈 리스트·language null)는 두 값을 null로 정규화해 폴백을 유도한다")
  void normalizes_legacy_set_conditions_to_null() {
    ProblemSet legacy =
        ProblemSet.builder()
            .id(1L)
            .sessionId("sess")
            .title("옛 세트")
            .generationStatus(GenerationStatus.COMPLETED)
            .quizType(QuizType.MULTIPLE)
            .totalQuizCount(5)
            .userId("user-1")
            .fileUrl("https://cdn.example/old.pdf")
            .pageNumbers(List.of()) // 컬럼 NULL → IntegerListConverter가 빈 리스트로 읽는다
            .language(null)
            .build();
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(legacy));

    RegenerationConditionResponse response = service.getRegenerationCondition("enc");

    assertThat(response.pageNumbers()).isNull();
    assertThat(response.language()).isNull();
    assertThat(response.documentAvailable()).isTrue();
  }

  @Test
  @DisplayName("세트가 없으면 PROBLEM_SET_NOT_FOUND")
  void throws_when_set_not_found_for_regeneration_condition() {
    when(hashUtil.decode("enc")).thenReturn(1L);
    when(problemSetRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getRegenerationCondition("enc"))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.PROBLEM_SET_NOT_FOUND.getMessage());
  }
}
