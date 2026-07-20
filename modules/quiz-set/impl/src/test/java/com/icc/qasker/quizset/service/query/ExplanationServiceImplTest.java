package com.icc.qasker.quizset.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.TestEntityFactory;
import com.icc.qasker.quizset.dto.feresponse.ExplanationResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemRepository;
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
class ExplanationServiceImplTest {

  @Mock private HashUtil hashUtil;
  @Mock private ProblemRepository problemRepository;
  @Mock private ProblemSetRepository problemSetRepository;

  private ExplanationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ExplanationServiceImpl(hashUtil, problemRepository, problemSetRepository);
    lenient().when(hashUtil.decode("enc")).thenReturn(1L);
  }

  private ProblemSet setWithFileUrl() {
    return TestEntityFactory.problemSet(
        1L,
        "sess",
        "t",
        com.icc.qasker.quizset.GenerationStatus.COMPLETED,
        com.icc.qasker.quizset.dto.ferequest.enums.QuizType.MULTIPLE,
        1,
        "u",
        List.of());
  }

  @Test
  @DisplayName("세트가 없으면 PROBLEM_SET_NOT_FOUND")
  void throws_when_set_not_found() {
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of());
    when(problemSetRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getExplanationByProblemSetId("enc"))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.PROBLEM_SET_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("문항이 없으면 PROBLEM_NOT_FOUND")
  void throws_when_problem_not_found() {
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of());
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setWithFileUrl()));

    assertThatThrownBy(() -> service.getExplanationByProblemSetId("enc"))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.PROBLEM_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("PROBLEMS_READY(해설 생성 진행 중) 세트는 오류가 아닌 '준비 중' 상태(generationStatus)와 함께 응답한다")
  void problems_ready_set_returns_status_instead_of_error() {
    Problem problem = TestEntityFactory.problem(1L, 1, "제목", List.of(), null, null, List.of());
    ProblemSet readySet =
        TestEntityFactory.problemSet(
            1L,
            "sess",
            "t",
            com.icc.qasker.quizset.GenerationStatus.PROBLEMS_READY,
            com.icc.qasker.quizset.dto.ferequest.enums.QuizType.MULTIPLE,
            1,
            "u",
            List.of());
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of(problem));
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(readySet));

    ExplanationResponse response = service.getExplanationByProblemSetId("enc");

    assertThat(response.generationStatus())
        .isEqualTo(com.icc.qasker.quizset.GenerationStatus.PROBLEMS_READY);
    assertThat(response.results().get(0).explanation()).isEqualTo("해설 준비 중입니다. 잠시 후 기다려 주세요.");
  }

  @Test
  @DisplayName("최초 상태(GENERATING)에서 해설이 없으면 '해설 없음'이 아니라 '해설 준비 중'으로 안내한다")
  void generating_set_shows_preparing_not_none() {
    Problem problem = TestEntityFactory.problem(1L, 1, "제목", List.of(), null, null, List.of());
    ProblemSet generatingSet =
        TestEntityFactory.problemSet(
            1L,
            "sess",
            "t",
            com.icc.qasker.quizset.GenerationStatus.GENERATING,
            com.icc.qasker.quizset.dto.ferequest.enums.QuizType.MULTIPLE,
            1,
            "u",
            List.of());
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of(problem));
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(generatingSet));

    ExplanationResponse response = service.getExplanationByProblemSetId("enc");

    assertThat(response.results().get(0).explanation()).isEqualTo("해설 준비 중입니다. 잠시 후 기다려 주세요.");
  }

  @Test
  @DisplayName("COMPLETED 세트는 COMPLETED 상태를 함께 응답한다")
  void completed_set_returns_completed_status() {
    Problem problem = TestEntityFactory.problem(1L, 1, "제목", List.of(), "완성된 해설", null, List.of());
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of(problem));
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setWithFileUrl()));

    ExplanationResponse response = service.getExplanationByProblemSetId("enc");

    assertThat(response.generationStatus())
        .isEqualTo(com.icc.qasker.quizset.GenerationStatus.COMPLETED);
    assertThat(response.results().get(0).explanation()).isEqualTo("완성된 해설");
  }

  @Test
  @DisplayName(
      "explanationContent가 null이고 종결(COMPLETED) 상태면 '해설 없음'으로 치환한다 — 현행 FE가 null 분기 없이 문자열을 기대(하위호환)")
  void null_explanation_falls_back_to_default() {
    Problem problem = TestEntityFactory.problem(1L, 1, "제목", List.of(), null, "지침", List.of(2, 5));
    when(problemRepository.findByIdProblemSetId(1L)).thenReturn(List.of(problem));
    when(problemSetRepository.findById(1L)).thenReturn(Optional.of(setWithFileUrl()));

    ExplanationResponse response = service.getExplanationByProblemSetId("enc");

    assertThat(response.results()).hasSize(1);
    assertThat(response.results().get(0).explanation()).isEqualTo("해설 없음");
    assertThat(response.results().get(0).number()).isEqualTo(1);
    assertThat(response.results().get(0).referencedPages()).containsExactly(2, 5);
    assertThat(response.fileUrl()).isEqualTo("file-url");
  }
}
