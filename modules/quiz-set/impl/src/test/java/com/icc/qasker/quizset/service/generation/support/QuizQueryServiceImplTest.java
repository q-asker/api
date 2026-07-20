package com.icc.qasker.quizset.service.generation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.TestEntityFactory;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.mapper.ProblemSetResponseMapper;
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
class QuizQueryServiceImplTest {

  @Mock private HashUtil hashUtil;
  @Mock private ProblemSetRepository problemSetRepository;
  @Mock private ProblemRepository problemRepository;

  private QuizQueryServiceImpl service;

  @BeforeEach
  void setUp() {
    ProblemSetResponseMapper mapper = TestEntityFactory.responseMapper(hashUtil);
    service = new QuizQueryServiceImpl(problemSetRepository, problemRepository, mapper);
  }

  @Test
  @DisplayName("getMissedProblems: 남은 문항만 담고 세트 필드가 응답과 일치한다")
  void get_missed_problems_maps_set_fields_and_remaining() {
    long setId = 7L;
    ProblemSet set =
        TestEntityFactory.problemSet(
            setId,
            "sess-9",
            "미완료 세트",
            GenerationStatus.GENERATING,
            QuizType.OX,
            10,
            "user-1",
            List.of());
    Problem remaining =
        TestEntityFactory.problem(
            setId,
            4,
            "남은 문항",
            List.of(new Selection("O", "설명O", true), new Selection("X", "설명X", false)),
            "해설",
            "지침",
            List.of());

    when(problemSetRepository.findFirstBySessionIdOrderByCreatedAtDesc("sess-9"))
        .thenReturn(Optional.of(set));
    when(problemRepository.findRemainingProblems(setId, 3)).thenReturn(List.of(remaining));
    when(hashUtil.encode(setId)).thenReturn("ENC-7");

    ProblemSetResponse response = service.getMissedProblems("sess-9", 3);

    assertThat(response.sessionId()).isEqualTo("sess-9");
    assertThat(response.problemSetId()).isEqualTo("ENC-7");
    assertThat(response.title()).isEqualTo("미완료 세트");
    assertThat(response.generationStatus()).isEqualTo(GenerationStatus.GENERATING);
    assertThat(response.quizType()).isEqualTo(QuizType.OX);
    assertThat(response.totalCount()).isEqualTo(10);
    assertThat(response.quiz()).hasSize(1);
    assertThat(response.quiz().get(0).number()).isEqualTo(4);
    assertThat(response.quiz().get(0).selections()).extracting("id").containsExactly(1, 2);

    // lastQuizNumber를 리포지토리 조회에 그대로 전달하는지 검증
    verify(problemRepository).findRemainingProblems(eq(setId), eq(3));
  }

  @Test
  @DisplayName("getMissedProblems: 세트가 없으면 PROBLEM_SET_NOT_FOUND")
  void get_missed_problems_throws_when_set_absent() {
    when(problemSetRepository.findFirstBySessionIdOrderByCreatedAtDesc("nope"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMissedProblems("nope", 0))
        .isInstanceOf(CustomException.class);
  }
}
