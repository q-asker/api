package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.dto.AISelection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배치 인터리빙 Phase 1 선지 정렬(SelectionArrangement) 단위 테스트 — 소비자에서 이관된 셔플/OX 정규화 회귀 방지. */
class SelectionArrangementTest {

  @Test
  @DisplayName("shuffle: 선지 집합은 보존된다(순서만 무작위화)")
  void shuffle_preserves_set() {
    List<AISelection> input =
        List.of(
            new AISelection("A", null, true),
            new AISelection("B", null, false),
            new AISelection("C", null, false),
            new AISelection("D", null, false));

    List<AISelection> result = SelectionArrangement.shuffle(input);

    assertThat(result)
        .extracting(AISelection::content)
        .containsExactlyInAnyOrder("A", "B", "C", "D");
    assertThat(result)
        .filteredOn(AISelection::correct)
        .extracting(AISelection::content)
        .containsExactly("A");
  }

  @Test
  @DisplayName("normalizeOxOrder: X가 1번이면 O가 1번이 되도록 순서를 바꾼다")
  void normalize_ox_moves_o_first() {
    List<AISelection> input =
        List.of(new AISelection("X", null, false), new AISelection("O", null, true));

    List<AISelection> result = SelectionArrangement.normalizeOxOrder(input);

    assertThat(result.get(0).content()).isEqualTo("O");
    assertThat(result.get(0).correct()).isTrue();
  }

  @Test
  @DisplayName("normalizeOxOrder: O가 이미 1번이면 순서를 유지한다")
  void normalize_ox_keeps_order_when_o_first() {
    List<AISelection> input =
        List.of(new AISelection("O", null, true), new AISelection("X", null, false));

    List<AISelection> result = SelectionArrangement.normalizeOxOrder(input);

    assertThat(result.get(0).content()).isEqualTo("O");
  }
}
