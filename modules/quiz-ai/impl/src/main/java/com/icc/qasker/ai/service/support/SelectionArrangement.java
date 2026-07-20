package com.icc.qasker.ai.service.support;

import com.icc.qasker.ai.dto.AISelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 배치 인터리빙 Phase 1에서 선지 저장 순서를 확정하는 정렬 유틸. 저장 순서 = 대화 히스토리(어시스턴트 턴) 순서 = Phase 2 해설 정렬 기준이므로,
 * 오케스트레이터가 저장 직전에 이 순서를 확정한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectionArrangement {

  /** MULTIPLE/BLANK: 선지 전체 셔플(정답 위치 무작위화). */
  public static List<AISelection> shuffle(List<AISelection> selections) {
    if (selections == null || selections.isEmpty()) return List.of();
    List<AISelection> copy = new ArrayList<>(selections);
    Collections.shuffle(copy);
    return List.copyOf(copy);
  }

  /** OX: X 계열이 1번이면 순서를 바꿔 O가 항상 1번이 되도록 정규화. */
  public static List<AISelection> normalizeOxOrder(List<AISelection> selections) {
    if (selections == null || selections.size() != 2) {
      return selections == null ? List.of() : List.copyOf(selections);
    }
    AISelection first = selections.get(0);
    if (first.content() != null && first.content().matches("(?i)^x$")) {
      return List.of(selections.get(1), selections.get(0));
    }
    return List.copyOf(selections);
  }
}
