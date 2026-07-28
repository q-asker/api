package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiRealBlankQuestion;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * GeminiRealBlankQuestion → AIProblemSet 변환. 정답(answer)을 단일 Selection(content=answer, correct=true,
 * acceptedAnswers=인정범위)로 매핑한다 — 오답 선택지 없음(FR-008). 해설은 selection.explanation에 담는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiRealBlankQuestionMapper {

  private static final Pattern PAGE_PATTERN = Pattern.compile("\\[(\\d+)p\\]\\s*>");

  public static AIProblemSet toDto(List<GeminiRealBlankQuestion> questions) {
    return toDto(questions, null);
  }

  public static AIProblemSet toDto(
      List<GeminiRealBlankQuestion> questions, List<Integer> sourcePages) {
    List<AIProblem> result =
        questions.stream()
            .map(
                q -> {
                  List<AISelection> selections =
                      q.answer() != null
                          ? List.of(
                              new AISelection(
                                  q.answer(),
                                  remapText(q.explanation(), sourcePages),
                                  true,
                                  q.acceptedAnswers()))
                          : List.of();

                  return new AIProblem(
                      q.content(),
                      q.bloomsLevel(),
                      selections,
                      remapPages(q.referencedPages(), sourcePages),
                      remapText(q.appliedInstruction(), sourcePages));
                })
            .toList();

    return new AIProblemSet(result);
  }

  private static String remapText(String text, List<Integer> sourcePages) {
    if (text == null || sourcePages == null || sourcePages.isEmpty()) {
      return text;
    }

    StringBuilder sb = new StringBuilder();
    Matcher matcher = PAGE_PATTERN.matcher(text);
    int lastEnd = 0;

    while (matcher.find()) {
      sb.append(text, lastEnd, matcher.start());
      try {
        int aiPage = Integer.parseInt(matcher.group(1));
        int index = aiPage - 1;
        if (index >= 0 && index < sourcePages.size()) {
          sb.append("[").append(sourcePages.get(index)).append("p] >");
        } else {
          sb.append(matcher.group());
        }
      } catch (NumberFormatException e) {
        sb.append(matcher.group());
      }
      lastEnd = matcher.end();
    }
    sb.append(text.substring(lastEnd));
    return sb.toString();
  }

  private static List<Integer> remapPages(List<Integer> aiPages, List<Integer> sourcePages) {
    if (aiPages == null) return List.of();
    if (sourcePages == null || sourcePages.isEmpty()) return aiPages;

    return aiPages.stream()
        .map(
            page -> {
              int index = page - 1;
              if (index >= 0 && index < sourcePages.size()) {
                return sourcePages.get(index);
              }
              return page;
            })
        .distinct()
        .sorted()
        .toList();
  }
}
