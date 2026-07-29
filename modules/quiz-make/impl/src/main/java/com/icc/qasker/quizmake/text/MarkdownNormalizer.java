package com.icc.qasker.quizmake.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 생성된 마크다운 본문의 결정론적 정규화. 유일한 복원 대상은 "GFM 표 구분행(`|---|`)이 인접한 헤더·본문 행과 한 줄로 글루된" 실패 모드로, 행 사이에 개행을
 * 재삽입해 렌더러가 표로 파싱하게 한다(재현 버그의 직접 원인).
 *
 * <p>설계 원칙:
 *
 * <ul>
 *   <li><b>보수적</b>: 구분행 시그니처가 없으면 완전 no-op → 서식 없는 본문 회귀 0(FR-006). 산문 속 리터럴 파이프(예: {@code P(A|B)},
 *       {@code |x|})는 구분행이 없으므로 건드리지 않는다.
 *   <li><b>멱등</b>: 이미 개행으로 분리된 정상 표는 변형하지 않는다. 복원 결과를 다시 정규화해도 동일하다.
 *   <li><b>무손실</b>: 어떤 경우에도 내용을 버리지 않는다. 복원이 모호하면(헤더 셀 수 불일치 등) 원문을 그대로 반환한다.
 * </ul>
 *
 * <p>구분행 자체가 소실된 표(구분행 없는 "파이프 수프")는 열·행 분할이 결정론적으로 모호해 복원 대상이 아니다 — 생성 프롬프트 서식 규약으로 예방한다(계약 §7.3-2
 * 경계).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkdownNormalizer {

  /**
   * GFM 표 구분행 셀: 선택적 정렬 콜론 + 하이픈 3개 이상. 예: {@code ---}, {@code :---}, {@code ---:}, {@code :---:}
   */
  private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{3,}:?$");

  /** 표 열로 인정할 최소 구분행 셀 수. 단일 하이픈 셀(수평선 등)의 오탐을 피하기 위해 2 이상. */
  private static final int MIN_COLUMNS = 2;

  public static String normalize(String text) {
    if (text == null || text.isBlank() || text.indexOf('|') < 0) {
      return text;
    }
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder(text.length() + 32);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(repairLine(lines[i]));
    }
    return out.toString();
  }

  /**
   * 한 줄 안에 헤더·구분행·본문이 글루된 표를 개행 분리된 표로 복원한다. 복원 대상이 아니면 원문을 그대로 반환한다.
   *
   * <p>개행이 유실되면 원래 행 경계({@code |\n|})는 빈 셀({@code | |})로 남는다. 이 빈 셀을 행 경계 구분자로 삼아 셀을 행 세그먼트로 나눈 뒤,
   * 구분행 세그먼트를 앵커로 표 구조를 재구성한다.
   */
  private static String repairLine(String line) {
    if (line.indexOf('|') < 0) {
      return line;
    }

    // 파이프 사이의 셀을 추출한다. parts[0]=첫 파이프 이전(표 밖 접두 텍스트), parts[last]=마지막 파이프 이후(표 밖 접미 텍스트).
    String[] parts = line.split("\\|", -1);
    if (parts.length < 3) {
      return line; // 파이프가 2개 미만이면 표가 아니다.
    }
    String prefix = parts[0];
    String suffix = parts[parts.length - 1];

    boolean hasSeparatorCell = false;
    for (int i = 1; i < parts.length - 1; i++) {
      if (SEPARATOR_CELL.matcher(parts[i].trim()).matches()) {
        hasSeparatorCell = true;
        break;
      }
    }
    if (!hasSeparatorCell) {
      return line; // 구분행 시그니처 없음 → 보수적으로 미변형(산문 속 리터럴 파이프 포함).
    }

    // 빈 셀(행 경계)을 기준으로 셀을 행 세그먼트로 나눈다.
    List<List<String>> segments = new ArrayList<>();
    List<String> current = new ArrayList<>();
    for (int i = 1; i < parts.length - 1; i++) {
      String cell = parts[i].trim();
      if (cell.isEmpty()) {
        if (!current.isEmpty()) {
          segments.add(current);
          current = new ArrayList<>();
        }
      } else {
        current.add(cell);
      }
    }
    if (!current.isEmpty()) {
      segments.add(current);
    }

    // 구분행 세그먼트(모든 셀이 구분행 시그니처)를 찾는다 — 정확히 1개여야 한다.
    int separatorIndex = -1;
    int separatorCount = 0;
    for (int i = 0; i < segments.size(); i++) {
      if (isSeparatorSegment(segments.get(i))) {
        separatorIndex = i;
        separatorCount++;
      }
    }
    if (separatorCount != 1) {
      return line; // 구분행이 없거나 모호(복수) → 미변형.
    }
    if (segments.size() == 1) {
      return line; // 구분행 세그먼트뿐(정상 단독 구분행 줄) → 멱등 no-op.
    }
    if (separatorIndex != 1) {
      return line; // 헤더는 정확히 세그먼트 0이어야 한다(구분행은 세그먼트 1).
    }

    int columns = segments.get(1).size();
    if (columns < MIN_COLUMNS) {
      return line; // 1열 표는 오탐 위험 → 미변형.
    }
    if (segments.get(0).size() != columns) {
      return line; // 헤더 셀 수 불일치 → 분할 모호 → 미변형(무손실).
    }
    // 본문 세그먼트: 마지막을 제외하고 모두 열 수와 일치해야 한다(마지막 잔여 행은 무손실 허용).
    for (int i = 2; i < segments.size(); i++) {
      int size = segments.get(i).size();
      boolean isLast = i == segments.size() - 1;
      if (size != columns && !(isLast && size < columns)) {
        return line; // 중간 행 셀 수 불일치 → 미변형(무손실).
      }
    }

    StringBuilder sb = new StringBuilder();
    if (!prefix.isBlank()) {
      sb.append(prefix.strip()).append("\n\n");
    }
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        sb.append('\n');
      }
      sb.append(renderRow(segments.get(i)));
    }
    if (!suffix.isBlank()) {
      sb.append("\n\n").append(suffix.strip());
    }
    return sb.toString();
  }

  private static boolean isSeparatorSegment(List<String> segment) {
    for (String cell : segment) {
      if (!SEPARATOR_CELL.matcher(cell).matches()) {
        return false;
      }
    }
    return !segment.isEmpty();
  }

  private static String renderRow(List<String> cells) {
    StringBuilder sb = new StringBuilder("|");
    for (String cell : cells) {
      sb.append(' ').append(cell).append(" |");
    }
    return sb.toString();
  }
}
