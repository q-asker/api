package com.icc.qasker.quizset.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.TestEntityFactory;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe.SelectionForFE;
import com.icc.qasker.quizset.dto.feresponse.RegenerationConditionResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.view.QuizView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemSetResponseMapperTest {

  @Mock private HashUtil hashUtil;

  private ProblemSetResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = TestEntityFactory.responseMapper(hashUtil);
  }

  private Problem sampleProblem() {
    return TestEntityFactory.problem(
        10L,
        3,
        "문항 제목",
        List.of(
            new Selection("보기A", "설명A", false),
            new Selection("보기B", "설명B", true),
            new Selection("보기C", "설명C", false)),
        "해설내용",
        "적용지침",
        List.of(1, 2));
  }

  @Test
  @DisplayName(
      "fromEntity(Problem): 풀이 응답 경량화 — explanation/선지 해설은 값 null(필드 유지), content·correct·appliedInstruction은 유지")
  void from_problem_maps_selections_and_initial_state() {
    QuizForFe quiz = mapper.fromEntity(sampleProblem());

    assertThat(quiz.number()).isEqualTo(3);
    assertThat(quiz.title()).isEqualTo("문항 제목");
    assertThat(quiz.userAnswer()).isEqualTo(0);
    assertThat(quiz.check()).isFalse();
    // 문항 해설은 FE 미사용 — 값 null로 경량화 (getExplanationContent 미접근 → Q6 lazy 성립 조건)
    assertThat(quiz.explanation()).isNull();
    // 적용 지침은 FE 풀이 화면이 사용 — 유지
    assertThat(quiz.appliedInstruction()).isEqualTo("적용지침");

    assertThat(quiz.selections()).extracting(SelectionForFE::id).containsExactly(1, 2, 3);
    // 선지별 해설은 값 null, content·correct는 유지(FE 클라이언트 채점 사용)
    assertThat(quiz.selections())
        .extracting(SelectionForFE::content, SelectionForFE::explanation, SelectionForFE::correct)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("보기A", null, false),
            org.assertj.core.groups.Tuple.tuple("보기B", null, true),
            org.assertj.core.groups.Tuple.tuple("보기C", null, false));
  }

  @Test
  @DisplayName("fromEntity(ProblemSet): hashUtil.encode 적용 및 세트 필드 매핑")
  void from_problem_set_maps_fields_with_encoded_id() {
    when(hashUtil.encode(10L)).thenReturn("ENC-10");
    Problem problem = sampleProblem();
    ProblemSet set =
        TestEntityFactory.problemSet(
            10L,
            "sess-1",
            "세트 제목",
            GenerationStatus.COMPLETED,
            QuizType.MULTIPLE,
            5,
            "user-1",
            List.of(problem));

    ProblemSetResponse response = mapper.fromEntity(set);

    assertThat(response.sessionId()).isEqualTo("sess-1");
    assertThat(response.problemSetId()).isEqualTo("ENC-10");
    assertThat(response.title()).isEqualTo("세트 제목");
    assertThat(response.generationStatus()).isEqualTo(GenerationStatus.COMPLETED);
    assertThat(response.quizType()).isEqualTo(QuizType.MULTIPLE);
    assertThat(response.totalCount()).isEqualTo(5);
    assertThat(response.quiz()).hasSize(1);
    assertThat(response.quiz().get(0).number()).isEqualTo(3);
  }

  @Test
  @DisplayName("toRegenerationCondition: 저장된 조건이 온전하면 그대로 싣고 documentAvailable=true")
  void regeneration_condition_carries_stored_conditions() {
    ProblemSet set =
        ProblemSet.builder()
            .id(10L)
            .sessionId("sess-1")
            .title("세트 제목")
            .generationStatus(GenerationStatus.COMPLETED)
            .quizType(QuizType.MULTIPLE)
            .totalQuizCount(5)
            .userId("user-1")
            .fileUrl("file-url")
            .customInstruction("지침")
            .pageNumbers(List.of(2, 3))
            .language("EN")
            .build();

    RegenerationConditionResponse response = mapper.toRegenerationCondition(set);

    assertThat(response.quizType()).isEqualTo(QuizType.MULTIPLE);
    assertThat(response.quizCount()).isEqualTo(5);
    assertThat(response.pageNumbers()).containsExactly(2, 3);
    assertThat(response.language()).isEqualTo("EN");
    assertThat(response.customInstruction()).isEqualTo("지침");
    assertThat(response.uploadedUrl()).isEqualTo("file-url");
    assertThat(response.title()).isEqualTo("세트 제목");
    assertThat(response.documentAvailable()).isTrue();
  }

  @Test
  @DisplayName("toRegenerationCondition: legacy 세트(pageNumbers 빈/ language 빈)는 두 값을 null로 내려 폴백 유도")
  void regeneration_condition_nulls_for_legacy_set() {
    ProblemSet legacy =
        ProblemSet.builder()
            .id(11L)
            .sessionId("sess-2")
            .title("legacy")
            .generationStatus(GenerationStatus.COMPLETED)
            .quizType(QuizType.OX)
            .totalQuizCount(10)
            .userId("user-1")
            .fileUrl("file-url")
            .pageNumbers(List.of())
            .language(null)
            .build();

    RegenerationConditionResponse response = mapper.toRegenerationCondition(legacy);

    assertThat(response.pageNumbers()).isNull();
    assertThat(response.language()).isNull();
    assertThat(response.documentAvailable()).isTrue();
  }

  @Test
  @DisplayName("ProblemToQuizViewMapper.toQuizView와 선택지 번호/내용/정답이 일관된다")
  void consistent_with_quiz_view_mapper() {
    Problem problem = sampleProblem();

    QuizForFe quiz = mapper.fromEntity(problem);
    QuizView view = ProblemToQuizViewMapper.toQuizView(problem);

    assertThat(view.getNumber()).isEqualTo(quiz.number());
    assertThat(view.getUserAnswer()).isEqualTo(quiz.userAnswer());
    assertThat(view.isCheck()).isEqualTo(quiz.check());
    assertThat(view.getSelections())
        .extracting(QuizView.SelectionView::getId)
        .containsExactly(1, 2, 3);
    for (int i = 0; i < quiz.selections().size(); i++) {
      assertThat(view.getSelections().get(i).getId()).isEqualTo(quiz.selections().get(i).id());
      assertThat(view.getSelections().get(i).getContent())
          .isEqualTo(quiz.selections().get(i).content());
      assertThat(view.getSelections().get(i).isCorrect())
          .isEqualTo(quiz.selections().get(i).correct());
    }
  }
}
