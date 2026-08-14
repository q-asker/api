package com.icc.qasker.quizmake.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockAIServerAdapterTest {

  private final MockAIServerAdapter adapter =
      new MockAIServerAdapter(mock(QuizOrchestrationService.class));

  private List<AIProblem> generate(String strategyValue, int quizCount) {
    List<AIProblem> collected = new ArrayList<>();
    QuizBatchSink sink =
        problem -> {
          collected.add(problem);
          return collected.size();
        };
    adapter.streamRequest(
        GenerationRequestToAI.builder()
            .quizType(strategyValue)
            .quizCount(quizCount)
            .referencePages(List.of(1, 2))
            .sink(sink)
            .build());
    return collected;
  }

  @Test
  @DisplayName("REAL_BLANK 전략은 정답 1선지(오답 없음) + 빈칸별 acceptedAnswers를 가진 목업을 낸다")
  void real_blank_strategy_emits_real_blank_mocks() {
    List<AIProblem> problems = generate("REAL_BLANK", 5);

    assertThat(problems).hasSize(5);
    assertThat(problems)
        .allSatisfy(
            p -> {
              assertThat(p.selections()).hasSize(1);
              assertThat(p.selections().get(0).correct()).isTrue();
              assertThat(p.selections().get(0).acceptedAnswers()).isNotEmpty();
              // index 0 = 모범답: 각 빈칸 인정 목록의 첫 원소가 대표정답의 해당 조각과 대응
              assertThat(p.selections().get(0).acceptedAnswers().get(0)).isNotEmpty();
            });
  }

  @Test
  @DisplayName("REAL_BLANK 목업은 단일 빈칸과 다중 빈칸(≥2)을 모두 포함한다 (FR-008 렌더 검증용)")
  void real_blank_mocks_include_single_and_multi_blank() {
    List<AIProblem> problems = generate("REAL_BLANK", 5);

    // 다중 빈칸: acceptedAnswers 바깥 배열 크기 ≥ 2
    assertThat(problems)
        .anySatisfy(
            p ->
                assertThat(p.selections().get(0).acceptedAnswers()).hasSizeGreaterThanOrEqualTo(2));
    // 단일 빈칸: acceptedAnswers 바깥 배열 크기 == 1
    assertThat(problems)
        .anySatisfy(p -> assertThat(p.selections().get(0).acceptedAnswers()).hasSize(1));
  }

  @Test
  @DisplayName("선택형(MULTIPLE) 전략은 기존 4선지 목업을 그대로 낸다 (회귀 방지)")
  void choice_strategy_keeps_four_selection_mocks() {
    List<AIProblem> problems = generate("MULTIPLE", 3);

    assertThat(problems).hasSize(3);
    assertThat(problems).allSatisfy(p -> assertThat(p.selections()).hasSize(4));
  }
}
