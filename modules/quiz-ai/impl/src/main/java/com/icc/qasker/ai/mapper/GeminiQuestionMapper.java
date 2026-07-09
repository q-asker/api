package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiQuestion;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** GeminiQuestion → AIProblemSet 변환. 문제+선택지+해설 원본 데이터를 매핑한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class GeminiQuestionMapper {

  // [Np] > 패턴을 찾는 정규표현식. N을 그룹 1로 캡처한다.
  private static final Pattern PAGE_PATTERN = Pattern.compile("\\[(\\d+)p\\]\\s*>");

  public static AIProblemSet toDto(List<GeminiQuestion> questions) {
    return toDto(questions, null);
  }

  /**
   * questions 목록을 AIProblemSet으로 변환한다.
   *
   * @param questions 파싱된 문제 목록
   * @param sourcePages 슬라이싱 시 사용된 원본 페이지 매핑 정보 (null이면 변환 없음)
   * @return 변환된 AIProblemSet
   */
  public static AIProblemSet toDto(List<GeminiQuestion> questions, List<Integer> sourcePages) {
    List<AIProblem> result =
        questions.stream()
            .map(
                q ->
                    new AIProblem(
                        q.content(),
                        q.bloomsLevel(),
                        q.selections() == null
                            ? List.of()
                            : q.selections().stream()
                                .map(
                                    s ->
                                        new AISelection(
                                            s.content(),
                                            remapText(s.explanation(), sourcePages),
                                            s.correct()))
                                .toList(),
                        remapPages(q.referencedPages(), sourcePages),
                        remapText(q.appliedInstruction(), sourcePages)))
            .toList();

    return new AIProblemSet(result);
  }

  /** 텍스트 내의 [Np] > 패턴을 찾아 원본 페이지 번호로 변환한다. 예: "[1p] >" -> "[10p] >" */
  private static String remapText(String text, List<Integer> sourcePages) {
    if (text == null || sourcePages == null || sourcePages.isEmpty()) {
      return text;
    }

    StringBuilder sb = new StringBuilder();
    Matcher matcher = PAGE_PATTERN.matcher(text);
    int lastEnd = 0;

    while (matcher.find()) {
      // 매칭된 부분 이전의 텍스트 추가
      sb.append(text, lastEnd, matcher.start());

      try {
        int aiPage = Integer.parseInt(matcher.group(1));
        int index = aiPage - 1;

        if (index >= 0 && index < sourcePages.size()) {
          // [원본p] > 형태로 교체하여 추가
          sb.append("[").append(sourcePages.get(index)).append("p] >");
        } else {
          // 범위를 벗어나면 원본 매칭 텍스트(예: [1p] >) 그대로 유지
          sb.append(matcher.group());
        }
      } catch (NumberFormatException e) {
        sb.append(matcher.group());
      }

      lastEnd = matcher.end();
    }
    // 남은 텍스트 추가
    sb.append(text.substring(lastEnd));

    return sb.toString();
  }

  private static List<Integer> remapPages(List<Integer> aiPages, List<Integer> sourcePages) {
    if (aiPages == null) return List.of();
    if (sourcePages == null || sourcePages.isEmpty()) return aiPages;

    return aiPages.stream()
        .map(
            page -> {
              // AI가 준 페이지는 1-based 인덱스로 가정
              int index = page - 1;
              if (index >= 0 && index < sourcePages.size()) {
                return sourcePages.get(index);
              }
              return page; // 범위를 벗어나면 원래 값 유지
            })
        .distinct()
        .sorted()
        .toList();
  }
}
