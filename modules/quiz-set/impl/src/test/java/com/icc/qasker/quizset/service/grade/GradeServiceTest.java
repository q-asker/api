package com.icc.qasker.quizset.service.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.GradeRequest;
import com.icc.qasker.quizset.dto.ferequest.GradeRequest.GradeAnswer;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.feresponse.GradeResponse;
import com.icc.qasker.quizset.dto.feresponse.GradeResponse.GradeResult;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

  @Mock private ProblemSetReadService problemSetReadService;
  @Mock private HashUtil hashUtil;
  @InjectMocks private GradeService gradeService;

  private static final long SET_ID = 42L;

  @BeforeEach
  void setUp() {
    lenient().when(hashUtil.decode("enc")).thenReturn(SET_ID);
  }

  private void givenSet(QuizType type, List<ProblemDetail> problems) {
    when(problemSetReadService.findProblemSetById(SET_ID))
        .thenReturn(
            java.util.Optional.of(
                new ProblemSetSummary(SET_ID, type, problems.size(), "t", Instant.EPOCH)));
    lenient().when(problemSetReadService.findProblemsByProblemSetId(SET_ID)).thenReturn(problems);
  }

  private ProblemDetail realBlankProblem(int number, String answer, List<List<String>> accepted) {
    return new ProblemDetail(
        number, "q" + number, List.of(new SelectionDetail(answer, true, accepted)), null);
  }

  private GradeResponse grade(int number, String textAnswer) {
    return gradeService.grade(
        new GradeRequest("enc", List.of(new GradeAnswer(number, textAnswer))));
  }

  @Test
  @DisplayName("동의어(영↔한)를 정답으로 인정하고 대표정답을 함께 준다 (FR-002)")
  void synonym_accepted() {
    givenSet(
        QuizType.REAL_BLANK,
        List.of(realBlankProblem(1, "미토콘드리아", List.of(List.of("미토콘드리아", "mitochondria")))));

    GradeResult result = grade(1, "mitochondria").results().getFirst();

    assertThat(result.isCorrect()).isTrue();
    assertThat(result.answer()).isEqualTo("미토콘드리아");
  }

  @Test
  @DisplayName("표기 차이만 있는 답을 정답으로 인정한다 (FR-001)")
  void notation_variant_accepted() {
    givenSet(QuizType.REAL_BLANK, List.of(realBlankProblem(1, "운동량", List.of(List.of("운동량")))));

    assertThat(grade(1, " 운동량!").results().getFirst().isCorrect()).isTrue();
  }

  @Test
  @DisplayName("인정 범위 밖(인접 개념)은 오답 (FR-005)")
  void adjacent_concept_wrong() {
    givenSet(
        QuizType.REAL_BLANK,
        List.of(realBlankProblem(1, "미토콘드리아", List.of(List.of("미토콘드리아", "mitochondria")))));

    assertThat(grade(1, "엽록체").results().getFirst().isCorrect()).isFalse();
  }

  @Test
  @DisplayName("인정 범위 정보가 없는 문항은 정답 content 완전 일치(정규화)로 폴백한다 (FR-009)")
  void fallback_to_exact_match() {
    givenSet(QuizType.REAL_BLANK, List.of(realBlankProblem(1, "운동량", null)));

    assertThat(grade(1, " 운동량 ").results().getFirst().isCorrect()).isTrue();
    assertThat(grade(1, "운동에너지").results().getFirst().isCorrect()).isFalse();
  }

  @Test
  @DisplayName("REAL_BLANK가 아닌 세트는 채점을 거부한다 (FR-007 격리)")
  void non_real_blank_rejected() {
    givenSet(QuizType.MULTIPLE, List.of());

    assertThatThrownBy(() -> grade(1, "x")).isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("응답에 빈칸별 허용 정답 목록을 실어 준다 — index 0 = 모범답 (FR-006·FR-008·US3)")
  void response_carries_accepted_answers_with_canonical_first() {
    givenSet(
        QuizType.REAL_BLANK,
        List.of(realBlankProblem(1, "운영체제", List.of(List.of("OS", "operating system", "운영체제")))));

    GradeResult result = grade(1, "OS").results().getFirst();

    assertThat(result.acceptedAnswers()).hasSize(1);
    assertThat(result.acceptedAnswers().get(0)).containsExactly("운영체제", "OS", "operating system");
  }

  @Test
  @DisplayName("legacy 문항(인정범위 null)도 응답에 대표정답 폴백 목록을 실어 준다 (FR-009)")
  void response_carries_fallback_for_legacy() {
    givenSet(QuizType.REAL_BLANK, List.of(realBlankProblem(1, "운동량", null)));

    GradeResult result = grade(1, "운동량").results().getFirst();

    assertThat(result.acceptedAnswers()).containsExactly(List.of("운동량"));
  }
}
