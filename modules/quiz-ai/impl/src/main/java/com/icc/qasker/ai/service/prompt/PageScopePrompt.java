package com.icc.qasker.ai.service.prompt;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 출제 범위(사용자가 고른 페이지) 지시. PDF 는 자르지 않고 원본 전체를 보내므로, 범위는 이 지시로만 좁힌다. 범위는 출제 대상(문제·정답)만 묶고 오답 선지는 묶지
 * 않는다 — 범위 밖 내용에서 가져온 오답이 그럴듯한 오답이 되기 때문이다. 유형 공통이라 청크 요청 프롬프트 앞에 붙인다 — 유형별 프롬프트의 맨 뒤(recency)는 사용자
 * 지시 override 자리라 비워 둔다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PageScopePrompt {

  /** 페이지가 없으면 빈 문자열(범위 제한 없음). */
  public static String of(List<Integer> pages) {
    if (pages == null || pages.isEmpty()) {
      return "";
    }
    return "[출제 범위] 제공된 파일의 물리적 페이지 순서(첫 페이지=1) 기준 %s페이지 내용으로 출제하세요.\n\n"
        .formatted(toRanges(pages));
  }

  /** 오름차순·중복 제거 후 연속 구간을 묶는다. 예: [3,1,2,5,7,8] → "1-3, 5, 7-8". 긴 선택도 토큰을 적게 쓰게 한다. */
  static String toRanges(List<Integer> pages) {
    List<Integer> sorted = pages.stream().distinct().sorted().toList();
    List<String> ranges = new ArrayList<>();
    int start = sorted.getFirst();
    int prev = start;
    for (int i = 1; i <= sorted.size(); i++) {
      if (i < sorted.size() && sorted.get(i) == prev + 1) {
        prev = sorted.get(i);
        continue;
      }
      ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
      if (i < sorted.size()) {
        start = sorted.get(i);
        prev = start;
      }
    }
    return String.join(", ", ranges);
  }
}
